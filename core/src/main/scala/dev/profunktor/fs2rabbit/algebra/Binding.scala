/*
 * Copyright 2017-2019 ProfunKtor
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

package dev.profunktor.fs2rabbit.algebra

import com.rabbitmq.client.Channel
import dev.profunktor.fs2rabbit.model._

object Binding {
  def apply[F[_]](implicit ev: Binding[F]): Binding[F] = ev
}

trait Binding[F[_]] {
  def bindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): F[Unit]
  def bindQueue(channel: Channel,
                queueName: QueueName,
                exchangeName: ExchangeName,
                routingKey: RoutingKey,
                args: QueueBindingArgs): F[Unit]
  def bindQueueNoWait(channel: Channel,
                      queueName: QueueName,
                      exchangeName: ExchangeName,
                      routingKey: RoutingKey,
                      args: QueueBindingArgs): F[Unit]
  def unbindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): F[Unit]
  def unbindQueue(channel: Channel,
                  queueName: QueueName,
                  exchangeName: ExchangeName,
                  routingKey: RoutingKey,
                  args: QueueUnbindArgs): F[Unit]
  def bindExchange(channel: Channel,
                   destination: ExchangeName,
                   source: ExchangeName,
                   routingKey: RoutingKey,
                   args: ExchangeBindingArgs): F[Unit]
  def bindExchangeNoWait(channel: Channel,
                         destination: ExchangeName,
                         source: ExchangeName,
                         routingKey: RoutingKey,
                         args: ExchangeBindingArgs): F[Unit]
  def unbindExchange(channel: Channel,
                     destination: ExchangeName,
                     source: ExchangeName,
                     routingKey: RoutingKey,
                     args: ExchangeUnbindArgs): F[Unit]
}
