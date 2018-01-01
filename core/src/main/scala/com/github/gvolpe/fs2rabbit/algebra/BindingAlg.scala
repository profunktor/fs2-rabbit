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

import com.github.gvolpe.fs2rabbit.model.{ExchangeBindingArgs, ExchangeName, QueueBindingArgs, QueueName, RoutingKey}
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel

trait BindingAlg[F[_]] {

  def bindQueue(channel: Channel,
                queueName: QueueName,
                exchangeName: ExchangeName,
                routingKey: RoutingKey): F[Queue.BindOk]

  def bindQueue(channel: Channel,
                queueName: QueueName,
                exchangeName: ExchangeName,
                routingKey: RoutingKey,
                args: QueueBindingArgs): F[Queue.BindOk]

  def bindQueueNoWait(channel: Channel,
                      queueName: QueueName,
                      exchangeName: ExchangeName,
                      routingKey: RoutingKey,
                      args: QueueBindingArgs): F[Unit]

  def unbindQueue(channel: Channel,
                  queueName: QueueName,
                  exchangeName: ExchangeName,
                  routingKey: RoutingKey): F[Queue.UnbindOk]

  def bindExchange(channel: Channel,
                   destination: ExchangeName,
                   source: ExchangeName,
                   routingKey: RoutingKey,
                   args: ExchangeBindingArgs): F[Exchange.BindOk]

}