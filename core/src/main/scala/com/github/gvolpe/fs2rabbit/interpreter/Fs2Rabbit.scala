/*
 * Copyright 2017 Fs2 Rabbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gvolpe.fs2rabbit.interpreter

import java.util.concurrent.Executors

import cats.effect.{Effect, IO}
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfigManager
import com.github.gvolpe.fs2rabbit.instances.log._
import com.github.gvolpe.fs2rabbit.instances.streameval._
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

import scala.concurrent.ExecutionContext

class Fs2Rabbit[F[_]: Effect] {

  implicit val queueEC: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val config    = new Fs2RabbitConfigManager[F].config
  private val internalQ = fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](100).unsafeRunSync()

  private implicit val amqpClient: AmqpClientStream[F] =
    new AmqpClientStream[F](internalQ)

  private val connectionStream: ConnectionStream[F] =
    new ConnectionStream[F](config)

  private implicit val ackerConsumerProgram: AckerConsumerProgram[F] =
    new AckerConsumerProgram[F](internalQ, config)

  private val consumingProgram: ConsumingProgram[F] =
    new ConsumingProgram[F]

  private val publishingProgram: PublishingProgram[F] =
    new PublishingProgram[F]

  def createConnectionChannel: Stream[F, Channel] = connectionStream.createConnectionChannel

  def createAckerConsumer(queueName: QueueName,
                          basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                          consumerArgs: Option[ConsumerArgs] = None)(
      implicit channel: Channel): Stream[F, (StreamAcker[F], StreamConsumer[F])] =
    consumingProgram.createAckerConsumer(channel, queueName, basicQos, consumerArgs)

  def createAutoAckConsumer(
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None)(implicit channel: Channel): Stream[F, StreamConsumer[F]] =
    consumingProgram.createAutoAckConsumer(channel, queueName, basicQos, consumerArgs)

  def createPublisher(exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: Channel): Stream[F, StreamPublisher[F]] =
    publishingProgram.createPublisher(channel, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: Channel): Stream[F, Queue.BindOk] =
    amqpClient.bindQueue(channel, queueName, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: Channel): Stream[F, Queue.BindOk] =
    amqpClient.bindQueue(channel, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: Channel): Stream[F, Unit] =
    amqpClient.bindQueueNoWait(channel, queueName, exchangeName, routingKey, args)

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: Channel): Stream[F, Queue.UnbindOk] =
    amqpClient.unbindQueue(channel, queueName, exchangeName, routingKey)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs)(
      implicit channel: Channel): Stream[F, Exchange.BindOk] =
    amqpClient.bindExchange(channel, destination, source, routingKey, args)

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: Channel): Stream[F, Exchange.DeclareOk] =
    amqpClient.declareExchange(channel, exchangeName, exchangeType)

  def declareQueue(queueName: QueueName)(implicit channel: Channel): Stream[F, Queue.DeclareOk] =
    amqpClient.declareQueue(channel, queueName)

  def deleteQueue(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
      implicit channel: Channel): Stream[F, Queue.DeleteOk] =
    amqpClient.deleteQueue(channel, queueName, ifUnused, ifEmpty)

  def deleteQueueNoWait(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
      implicit channel: Channel): Stream[F, Unit] =
    amqpClient.deleteQueueNoWait(channel, queueName, ifUnused, ifEmpty)

}
