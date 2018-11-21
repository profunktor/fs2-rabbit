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

import com.github.gvolpe.fs2rabbit.effects.MessageEncoder
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel

trait Publishing[F[_], G[_]] {

  def createPublisher[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit encoder: MessageEncoder[G, A]): F[A => G[Unit]]

  def createPublisherWithListener[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flags: PublishingFlag,
      listener: PublishReturn => G[Unit]
  )(implicit encoder: MessageEncoder[G, A]): F[A => G[Unit]]

  def createRoutingPublisher[A](
      channel: Channel,
      exchangeName: ExchangeName
  )(implicit encoder: MessageEncoder[G, A]): F[RoutingKey => A => G[Unit]]

  def createRoutingPublisherWithListener[A](
      channel: Channel,
      exchangeName: ExchangeName,
      flags: PublishingFlag,
      listener: PublishReturn => G[Unit]
  )(implicit encoder: MessageEncoder[G, A]): F[RoutingKey => A => G[Unit]]

}
