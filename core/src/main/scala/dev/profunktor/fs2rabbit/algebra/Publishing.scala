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

import cats.effect.Blocker
import com.rabbitmq.client.Channel
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model._

trait Publishing[F[_]] {

  def createPublisher[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A]): F[A => F[Unit]]

  def createPublisherWithListener[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit],
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A]): F[A => F[Unit]]

  def createRoutingPublisher[A](
      channel: Channel,
      exchangeName: ExchangeName,
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]]

  def createRoutingPublisherWithListener[A](
      channel: Channel,
      exchangeName: ExchangeName,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit],
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]]

  def createBasicPublisher[A](
      channel: Channel,
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A]): F[(ExchangeName, RoutingKey, A) => F[Unit]]

  def createBasicPublisherWithListener[A](
      channel: Channel,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit],
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A]): F[(ExchangeName, RoutingKey, A) => F[Unit]]

}
