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

package dev.profunktor.fs2rabbit.interpreter

import cats.effect.{Effect, _}
import cats.syntax.functor._
import com.rabbitmq.client._
import dev.profunktor.fs2rabbit.algebra.Binding
import dev.profunktor.fs2rabbit.arguments._
import dev.profunktor.fs2rabbit.model._

object BindingEffect {
  def apply[F[_]: Effect]: Binding[F] =
    new BindingEffect[F] { override lazy val effect: Effect[F] = Effect[F] }
}

trait BindingEffect[F[_]] extends Binding[F] {
  implicit val effect: Effect[F]

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey): F[Unit] =
    Sync[F].delay {
      channel.queueBind(queueName.value, exchangeName.value, routingKey.value)
    }.void

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey,
                         args: QueueBindingArgs): F[Unit] =
    Sync[F].delay {
      channel.queueBind(
        queueName.value,
        exchangeName.value,
        routingKey.value,
        args.value
      )
    }.void

  override def bindQueueNoWait(channel: Channel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey,
                               args: QueueBindingArgs): F[Unit] =
    Sync[F].delay {
      channel.queueBindNoWait(
        queueName.value,
        exchangeName.value,
        routingKey.value,
        args.value
      )
    }.void

  override def unbindQueue(channel: Channel,
                           queueName: QueueName,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey): F[Unit] =
    Sync[F].delay {
      unbindQueue(
        channel,
        queueName,
        exchangeName,
        routingKey,
        QueueUnbindArgs(Map.empty)
      )
    }.void

  override def unbindQueue(channel: Channel,
                           queueName: QueueName,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey,
                           args: QueueUnbindArgs): F[Unit] =
    Sync[F].delay {
      channel.queueUnbind(
        queueName.value,
        exchangeName.value,
        routingKey.value,
        args.value
      )
    }.void

  override def bindExchange(channel: Channel,
                            destination: ExchangeName,
                            source: ExchangeName,
                            routingKey: RoutingKey,
                            args: ExchangeBindingArgs): F[Unit] =
    Sync[F].delay {
      channel.exchangeBind(
        destination.value,
        source.value,
        routingKey.value,
        args.value
      )
    }.void

  override def bindExchangeNoWait(channel: Channel,
                                  destination: ExchangeName,
                                  source: ExchangeName,
                                  routingKey: RoutingKey,
                                  args: ExchangeBindingArgs): F[Unit] =
    Sync[F].delay {
      channel.exchangeBindNoWait(
        destination.value,
        source.value,
        routingKey.value,
        args.value
      )
    }.void

  override def unbindExchange(channel: Channel,
                              destination: ExchangeName,
                              source: ExchangeName,
                              routingKey: RoutingKey,
                              args: ExchangeUnbindArgs): F[Unit] =
    Sync[F].delay {
      channel.exchangeUnbind(
        destination.value,
        source.value,
        routingKey.value,
        args.value
      )
    }.void

}
