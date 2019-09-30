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

package dev.profunktor.fs2rabbit.program

import cats.effect.{Blocker, ContextShift}
import cats.{Applicative, Monad}
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.{AMQPClient, Publishing}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model._
import com.rabbitmq.client.Channel

class PublishingProgram[F[_]: Monad](AMQP: AMQPClient[F]) extends Publishing[F] {

  override def createPublisher[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A], cs: ContextShift[F]): F[A => F[Unit]] =
    createRoutingPublisher(channel, exchangeName, blocker).map(_.apply(routingKey))

  override def createPublisherWithListener[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit],
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A], cs: ContextShift[F]): F[A => F[Unit]] =
    createRoutingPublisherWithListener(channel, exchangeName, flag, listener, blocker).map(_.apply(routingKey))

  override def createRoutingPublisher[A](
      channel: Channel,
      exchangeName: ExchangeName,
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A], cs: ContextShift[F]): F[RoutingKey => A => F[Unit]] =
    createBasicPublisher(channel, blocker).map(pub => key => msg => pub(exchangeName, key, msg))

  override def createRoutingPublisherWithListener[A](
      channel: Channel,
      exchangeName: ExchangeName,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit],
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A], cs: ContextShift[F]): F[RoutingKey => A => F[Unit]] =
    createBasicPublisherWithListener(channel, flag, listener, blocker).map(
      pub => key => msg => pub(exchangeName, key, msg)
    )

  override def createBasicPublisher[A](
      channel: Channel,
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A], cs: ContextShift[F]): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    Applicative[F].pure {
      case (exchangeName: ExchangeName, routingKey: RoutingKey, msg: A @unchecked) =>
        encoder.run(msg).flatMap(payload => AMQP.basicPublish(channel, exchangeName, routingKey, payload, blocker))
    }

  override def createBasicPublisherWithListener[A](
      channel: Channel,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit],
      blocker: Blocker
  )(implicit encoder: MessageEncoder[F, A], cs: ContextShift[F]): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    AMQP.addPublishingListener(channel, listener).as {
      case (exchangeName: ExchangeName, routingKey: RoutingKey, msg: A @unchecked) =>
        encoder
          .run(msg)
          .flatMap(payload => AMQP.basicPublishWithFlag(channel, exchangeName, routingKey, flag, payload, blocker))
    }
}
