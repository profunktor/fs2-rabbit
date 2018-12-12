/*
 * Copyright 2017-2019 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.algebra

import com.github.gvolpe.fs2rabbit.arguments.Arguments
import com.github.gvolpe.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel

// format: off
trait AMQPClient[F[_]] extends Binding[F] with Declaration[F] with Deletion[F] {
  def basicAck(channel: Channel, tag: DeliveryTag, multiple: Boolean): F[Unit]
  def basicNack(channel: Channel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]
  def basicQos(channel: Channel, basicQos: BasicQos): F[Unit]
  def basicConsume[A](channel: Channel, queueName: QueueName, autoAck: Boolean, consumerTag: ConsumerTag, noLocal: Boolean, exclusive: Boolean, args: Arguments)
                  (internals: AQMPInternals[F]): F[ConsumerTag]
  def basicCancel(channel: Channel, consumerTag: ConsumerTag): F[Unit]
  def basicPublish(channel: Channel, exchangeName: ExchangeName, routingKey: RoutingKey, msg: AmqpMessage[Array[Byte]]): F[Unit]
  def basicPublishWithFlag(channel: Channel, exchangeName: ExchangeName, routingKey: RoutingKey, flag: PublishingFlag, msg: AmqpMessage[Array[Byte]]): F[Unit]
  def addPublishingListener(channel: Channel, listener: PublishReturn => F[Unit]): F[Unit]
  def clearPublishingListeners(channel: Channel): F[Unit]
}

trait Binding[F[_]] {
  def bindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): F[Unit]
  def bindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs): F[Unit]
  def bindQueueNoWait(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs): F[Unit]
  def unbindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): F[Unit]
  def unbindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueUnbindArgs): F[Unit]
  def bindExchange(channel: Channel, destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs): F[Unit]
  def bindExchangeNoWait(channel: Channel, destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs): F[Unit]
  def unbindExchange(channel: Channel, destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeUnbindArgs): F[Unit]
}

trait Declaration[F[_]] {
  def declareExchange(channel: Channel, exchangeConfig: DeclarationExchangeConfig): F[Unit]
  def declareExchangeNoWait(value: Channel, exchangeConfig: DeclarationExchangeConfig): F[Unit]
  def declareExchangePassive(channel: Channel, exchangeName: ExchangeName): F[Unit]
  def declareQueue(channel: Channel, queueConfig: DeclarationQueueConfig): F[Unit]
  def declareQueueNoWait(channel: Channel, queueConfig: DeclarationQueueConfig): F[Unit]
  def declareQueuePassive(channel: Channel, queueName: QueueName): F[Unit]
}

trait Deletion[F[_]] {
  def deleteQueue(channel: Channel, config: DeletionQueueConfig): F[Unit]
  def deleteQueueNoWait(channel: Channel, config: DeletionQueueConfig): F[Unit]

  def deleteExchange(channel: Channel, config: DeletionExchangeConfig): F[Unit]
  def deleteExchangeNoWait(channel: Channel, config: DeletionExchangeConfig): F[Unit]
}
