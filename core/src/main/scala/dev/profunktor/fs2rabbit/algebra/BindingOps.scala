/*
 * Copyright 2017-2024 ProfunKtor
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

import cats.~>
import dev.profunktor.fs2rabbit.model._

private[fs2rabbit] final class BindingOps[F[_]](val binding: Binding[F]) extends AnyVal {
  def mapK[G[_]](fK: F ~> G): Binding[G] = new Binding[G] {
    def bindQueue(
        channel: AMQPChannel,
        queueName: QueueName,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        args: QueueBindingArgs
    ): G[Unit] =
      fK(binding.bindQueue(channel, queueName, exchangeName, routingKey, args))

    def bindQueueNoWait(
        channel: AMQPChannel,
        queueName: QueueName,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        args: QueueBindingArgs
    ): G[Unit] =
      fK(binding.bindQueueNoWait(channel, queueName, exchangeName, routingKey, args))

    def unbindQueue(
        channel: AMQPChannel,
        queueName: QueueName,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        args: QueueUnbindArgs
    ): G[Unit] =
      fK(binding.unbindQueue(channel, queueName, exchangeName, routingKey, args))

    def bindExchange(
        channel: AMQPChannel,
        destination: ExchangeName,
        source: ExchangeName,
        routingKey: RoutingKey,
        args: ExchangeBindingArgs
    ): G[Unit] =
      fK(binding.bindExchange(channel, destination, source, routingKey, args))

    def bindExchangeNoWait(
        channel: AMQPChannel,
        destination: ExchangeName,
        source: ExchangeName,
        routingKey: RoutingKey,
        args: ExchangeBindingArgs
    ): G[Unit] =
      fK(binding.bindExchangeNoWait(channel, destination, source, routingKey, args))

    def unbindExchange(
        channel: AMQPChannel,
        destination: ExchangeName,
        source: ExchangeName,
        routingKey: RoutingKey,
        args: ExchangeUnbindArgs
    ): G[Unit] =
      fK(binding.unbindExchange(channel, destination, source, routingKey, args))
  }
}
