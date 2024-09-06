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

import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model._

trait Publishing[F[_]] {

  def createPublisher[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit encoder: MessageEncoder[F, A]): F[A => F[Unit]]

  def createPublisherWithListener[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit encoder: MessageEncoder[F, A]): F[A => F[Unit]]

  def createRoutingPublisher[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName
  )(implicit encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]]

  def createRoutingPublisherWithListener[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]]

  def createBasicPublisher[A](
      channel: AMQPChannel
  )(implicit encoder: MessageEncoder[F, A]): F[(ExchangeName, RoutingKey, A) => F[Unit]]

  def createBasicPublisherWithListener[A](
      channel: AMQPChannel,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit encoder: MessageEncoder[F, A]): F[(ExchangeName, RoutingKey, A) => F[Unit]]

}
