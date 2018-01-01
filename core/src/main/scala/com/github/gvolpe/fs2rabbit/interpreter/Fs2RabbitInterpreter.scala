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
import com.github.gvolpe.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitConfigManager}
import com.github.gvolpe.fs2rabbit.instances.log._
import com.github.gvolpe.fs2rabbit.instances.streameval._
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

// TODO: Remove Channel param from all the methods and make it implicit
class Fs2RabbitInterpreter[F[_] : Async] {

  private lazy val config: F[Fs2RabbitConfig] =
    new Fs2RabbitConfigManager[F].config

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

  def createAckerConsumer(channel: Channel,
                          queueName: QueueName,
                          basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                          consumerArgs: Option[ConsumerArgs] = None): Stream[F, (StreamAcker[F], StreamConsumer[F])] =
    consumingProgram.createAckerConsumer(channel, queueName, basicQos, consumerArgs)

  def createAutoAckConsumer(channel: Channel,
                            queueName: QueueName,
                            basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                            consumerArgs: Option[ConsumerArgs] = None): StreamConsumer[F] =
    consumingProgram.createAutoAckConsumer(channel, queueName, basicQos, consumerArgs)

  def createPublisher(channel: Channel,
                      exchangeName: ExchangeName,
                      routingKey: RoutingKey): Stream[F, StreamPublisher[F]] =
    publishingProgram.createPublisher(channel, exchangeName, routingKey)

  def bindQueue(channel: Channel,
                queueName: QueueName,
                exchangeName: ExchangeName,
                routingKey: RoutingKey): Stream[F, Queue.BindOk] =
    bindingProgram.bindQueue(channel, queueName, exchangeName, routingKey)

  def bindQueue(channel: Channel,
                queueName: QueueName,
                exchangeName: ExchangeName,
                routingKey: RoutingKey,
                args: QueueBindingArgs): Stream[F, Queue.BindOk] =
    bindingProgram.bindQueue(channel, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(channel: Channel,
                      queueName: QueueName,
                      exchangeName: ExchangeName,
                      routingKey: RoutingKey,
                      args: QueueBindingArgs): Stream[F, Unit] =
    bindingProgram.bindQueueNoWait(channel, queueName, exchangeName, routingKey, args)

  def unbindQueue(channel: Channel,
                  queueName: QueueName,
                  exchangeName: ExchangeName,
                  routingKey: RoutingKey): Stream[F, Queue.UnbindOk] =
    bindingProgram.unbindQueue(channel, queueName, exchangeName, routingKey)

  def bindExchange(channel: Channel,
                   destination: ExchangeName,
                   source: ExchangeName,
                   routingKey: RoutingKey,
                   args: ExchangeBindingArgs): Stream[F, Exchange.BindOk] =
    bindingProgram.bindExchange(channel, destination, source, routingKey, args)

  def declareExchange(channel: Channel,
                      exchangeName: ExchangeName,
                      exchangeType: ExchangeType): Stream[F, Exchange.DeclareOk] =
    declarationProgram.declareExchange(channel, exchangeName, exchangeType)

  def declareQueue(channel: Channel, queueName: QueueName): Stream[F, Queue.DeclareOk] =
    declarationProgram.declareQueue(channel, queueName)

  def deleteQueue(channel: Channel,
                  queueName: QueueName,
                  ifUnused: Boolean = true,
                  ifEmpty: Boolean = true): Stream[F, Queue.DeleteOk] =
    deletionProgram.deleteQueue(channel, queueName, ifUnused, ifEmpty)

  def deleteQueueNoWait(channel: Channel,
                        queueName: QueueName,
                        ifUnused: Boolean = true,
                        ifEmpty: Boolean = true): Stream[F,Unit] =
    deletionProgram.deleteQueueNoWait(channel, queueName, ifUnused, ifEmpty)

}
