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

import cats.effect.Async
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.instances.log._
import com.github.gvolpe.fs2rabbit.instances.streameval._
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

class Fs2RabbitInterpreter[F[_]: Async](config: F[Fs2RabbitConfig]) {

  private implicit val amqpClientProgram: AmqpClientProgram[F] =
    new AmqpClientProgram[F](config)

  private val connectionProgram: ConnectionProgram[F] =
    new ConnectionProgram[F](config)

  private val consumingProgram: ConsumingProgram[F] =
    new ConsumingProgram[F]

  private val publishingProgram: PublishingProgram[F] =
    new PublishingProgram[F]

  private val bindingProgram: BindingProgram[F] =
    new BindingProgram[F]

  private val declarationProgram: DeclarationProgram[F] =
    new DeclarationProgram[F]

  private val deletionProgram: DeletionProgram[F] =
    new DeletionProgram[F]

  def createConnectionChannel: Stream[F, Channel] = connectionProgram.createConnectionChannel

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
    bindingProgram.bindQueue(channel, queueName, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: Channel): Stream[F, Queue.BindOk] =
    bindingProgram.bindQueue(channel, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: Channel): Stream[F, Unit] =
    bindingProgram.bindQueueNoWait(channel, queueName, exchangeName, routingKey, args)

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: Channel): Stream[F, Queue.UnbindOk] =
    bindingProgram.unbindQueue(channel, queueName, exchangeName, routingKey)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs)(
      implicit channel: Channel): Stream[F, Exchange.BindOk] =
    bindingProgram.bindExchange(channel, destination, source, routingKey, args)

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: Channel): Stream[F, Exchange.DeclareOk] =
    declarationProgram.declareExchange(channel, exchangeName, exchangeType)

  def declareQueue(queueName: QueueName)(implicit channel: Channel): Stream[F, Queue.DeclareOk] =
    declarationProgram.declareQueue(channel, queueName)

  def deleteQueue(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
      implicit channel: Channel): Stream[F, Queue.DeleteOk] =
    deletionProgram.deleteQueue(channel, queueName, ifUnused, ifEmpty)

  def deleteQueueNoWait(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
      implicit channel: Channel): Stream[F, Unit] =
    deletionProgram.deleteQueueNoWait(channel, queueName, ifUnused, ifEmpty)

}
