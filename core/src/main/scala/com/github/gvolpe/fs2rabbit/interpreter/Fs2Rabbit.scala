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
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, Connection}
import com.github.gvolpe.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitConfigManager}
import com.github.gvolpe.fs2rabbit.instances.log._
import com.github.gvolpe.fs2rabbit.instances.streameval._
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import fs2.Stream
import fs2.async.mutable.Queue

import scala.concurrent.ExecutionContext

object Fs2Rabbit {
  def apply[F[_]](implicit F: Effect[F]): Fs2Rabbit[F] = {
    implicit val queueEC: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
    val interpreter = for {
      internalQ  <- fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](500)
      amqpClient <- IO(new AmqpClientStream[F](internalQ))
      config     <- IO(new Fs2RabbitConfigManager[F].config)
      connStream <- IO(new ConnectionStream[F](config))
      fs2Rabbit  <- IO(new Fs2Rabbit[F](config, connStream, internalQ)(F, amqpClient))
    } yield fs2Rabbit
    interpreter.unsafeRunSync()
  }
}

class Fs2Rabbit[F[_]](config: F[Fs2RabbitConfig],
                      connectionStream: Connection[F, Stream[F, ?]],
                      internalQ: Queue[IO, Either[Throwable, AmqpEnvelope]])(implicit F: Effect[F],
                                                                             amqpClient: AMQPClient[Stream[F, ?]]) {

  private implicit val ackerConsumerProgram: AckerConsumerProgram[F] =
    new AckerConsumerProgram[F](internalQ, config)

  private val consumingProgram: ConsumingProgram[F] =
    new ConsumingProgram[F]

  private val publishingProgram: PublishingProgram[F] =
    new PublishingProgram[F]

  def createConnectionChannel: Stream[F, AMQPChannel] = connectionStream.createConnectionChannel

  def createAckerConsumer(queueName: QueueName,
                          basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                          consumerArgs: Option[ConsumerArgs] = None)(
      implicit channel: AMQPChannel): Stream[F, (StreamAcker[F], StreamConsumer[F])] =
    consumingProgram.createAckerConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createAutoAckConsumer(
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None)(implicit channel: AMQPChannel): Stream[F, StreamConsumer[F]] =
    consumingProgram.createAutoAckConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createPublisher(exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, StreamPublisher[F]] =
    publishingProgram.createPublisher(channel.value, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueueNoWait(channel.value, queueName, exchangeName, routingKey, args)

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.unbindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindExchange(channel.value, destination, source, routingKey, args)

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchange(channel.value, exchangeName, exchangeType)

  def declareQueue(queueName: QueueName)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareQueue(channel.value, queueName)

  def deleteQueue(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteQueue(channel.value, queueName, ifUnused, ifEmpty)

  def deleteQueueNoWait(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteQueueNoWait(channel.value, queueName, ifUnused, ifEmpty)

}
