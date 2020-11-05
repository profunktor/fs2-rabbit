/*
 * Copyright 2017-2020 ProfunKtor
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

import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.arguments._
import dev.profunktor.fs2rabbit.model._

object Binding {
  def make[F[_]: Sync: ContextShift](blocker: Blocker): Binding[F] =
    new Binding[F] {
      override def bindQueue(channel: AMQPChannel,
                             queueName: QueueName,
                             exchangeName: ExchangeName,
                             routingKey: RoutingKey): F[Unit] =
        blocker.delay {
          channel.value.queueBind(
            queueName.value,
            exchangeName.value,
            routingKey.value
          )
        }.void

      override def bindQueue(channel: AMQPChannel,
                             queueName: QueueName,
                             exchangeName: ExchangeName,
                             routingKey: RoutingKey,
                             args: QueueBindingArgs): F[Unit] =
        blocker.delay {
          channel.value.queueBind(
            queueName.value,
            exchangeName.value,
            routingKey.value,
            args.value
          )
        }.void

      override def bindQueueNoWait(channel: AMQPChannel,
                                   queueName: QueueName,
                                   exchangeName: ExchangeName,
                                   routingKey: RoutingKey,
                                   args: QueueBindingArgs): F[Unit] =
        blocker.delay {
          channel.value.queueBindNoWait(
            queueName.value,
            exchangeName.value,
            routingKey.value,
            args.value
          )
        }.void

      override def unbindQueue(channel: AMQPChannel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey): F[Unit] =
        unbindQueue(
          channel,
          queueName,
          exchangeName,
          routingKey,
          QueueUnbindArgs(Map.empty)
        )

      override def unbindQueue(channel: AMQPChannel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey,
                               args: QueueUnbindArgs): F[Unit] =
        blocker.delay {
          channel.value.queueUnbind(
            queueName.value,
            exchangeName.value,
            routingKey.value,
            args.value
          )
        }.void

      override def bindExchange(channel: AMQPChannel,
                                destination: ExchangeName,
                                source: ExchangeName,
                                routingKey: RoutingKey,
                                args: ExchangeBindingArgs): F[Unit] =
        blocker.delay {
          channel.value.exchangeBind(
            destination.value,
            source.value,
            routingKey.value,
            args.value
          )
        }.void

      override def bindExchangeNoWait(channel: AMQPChannel,
                                      destination: ExchangeName,
                                      source: ExchangeName,
                                      routingKey: RoutingKey,
                                      args: ExchangeBindingArgs): F[Unit] =
        blocker.delay {
          channel.value.exchangeBindNoWait(
            destination.value,
            source.value,
            routingKey.value,
            args.value
          )
        }.void

      override def unbindExchange(channel: AMQPChannel,
                                  destination: ExchangeName,
                                  source: ExchangeName,
                                  routingKey: RoutingKey,
                                  args: ExchangeUnbindArgs): F[Unit] =
        blocker.delay {
          channel.value.exchangeUnbind(
            destination.value,
            source.value,
            routingKey.value,
            args.value
          )
        }.void
    }
}

trait Binding[F[_]] {
  def bindQueue(channel: AMQPChannel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): F[Unit]
  def bindQueue(channel: AMQPChannel,
                queueName: QueueName,
                exchangeName: ExchangeName,
                routingKey: RoutingKey,
                args: QueueBindingArgs): F[Unit]
  def bindQueueNoWait(channel: AMQPChannel,
                      queueName: QueueName,
                      exchangeName: ExchangeName,
                      routingKey: RoutingKey,
                      args: QueueBindingArgs): F[Unit]
  def unbindQueue(channel: AMQPChannel,
                  queueName: QueueName,
                  exchangeName: ExchangeName,
                  routingKey: RoutingKey): F[Unit]
  def unbindQueue(channel: AMQPChannel,
                  queueName: QueueName,
                  exchangeName: ExchangeName,
                  routingKey: RoutingKey,
                  args: QueueUnbindArgs): F[Unit]
  def bindExchange(channel: AMQPChannel,
                   destination: ExchangeName,
                   source: ExchangeName,
                   routingKey: RoutingKey,
                   args: ExchangeBindingArgs): F[Unit]
  def bindExchangeNoWait(channel: AMQPChannel,
                         destination: ExchangeName,
                         source: ExchangeName,
                         routingKey: RoutingKey,
                         args: ExchangeBindingArgs): F[Unit]
  def unbindExchange(channel: AMQPChannel,
                     destination: ExchangeName,
                     source: ExchangeName,
                     routingKey: RoutingKey,
                     args: ExchangeUnbindArgs): F[Unit]
}
