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

package com.github.gvolpe.fs2rabbit.algebra

import com.github.gvolpe.fs2rabbit.config.QueueConfig
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel

// format: off
trait AMQPClient[F[_]] extends Binding[F] with Declaration[F] with Deletion[F] {
  def basicAck(channel: Channel, tag: DeliveryTag, multiple: Boolean): F[Unit]
  def basicNack(channel: Channel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]
  def basicQos(channel: Channel, basicQos: BasicQos): F[Unit]
  def basicConsume(channel: Channel, queueName: QueueName, autoAck: Boolean, consumerTag: String, noLocal: Boolean, exclusive: Boolean, args: Map[String, AnyRef]): F[String]
  def basicPublish(channel: Channel, exchangeName: ExchangeName, routingKey: RoutingKey, msg: AmqpMessage[String]): F[Unit]
}

trait Binding[F[_]] {
  def bindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): F[Unit]
  def bindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs): F[Unit]
  def bindQueueNoWait(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs): F[Unit]
  def unbindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): F[Unit]
  def bindExchange(channel: Channel, destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs): F[Unit]
}

trait Declaration[F[_]] {
  def declareExchange(channel: Channel, exchangeName: ExchangeName, exchangeType: ExchangeType): F[Unit]
  def declareQueue(channel: Channel, queueConfig: QueueConfig): F[Unit]
  def declareQueueNoWait(channel: Channel, queueConfig: QueueConfig): F[Unit]
  def declareQueuePassive(channel: Channel, queueName: QueueName): F[Unit]
}

trait Deletion[F[_]] {
  def deleteQueue(channel: Channel, queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true): F[Unit]
  def deleteQueueNoWait(channel: Channel, queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true): F[Unit]
}