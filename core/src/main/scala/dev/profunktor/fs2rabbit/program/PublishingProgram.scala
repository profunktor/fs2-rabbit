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

package dev.profunktor.fs2rabbit.program

import cats.Applicative
import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.{Publish, Publishing}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model._

object PublishingProgram {
  def make[F[_]: Sync](dispatcher: Dispatcher[F]): PublishingProgram[F] =
    WrapperPublishingProgram(Publish.make(dispatcher))

  implicit def toPublishingProgramOps[F[_]](prog: PublishingProgram[F]): PublishingProgramOps[F] =
    new PublishingProgramOps[F](prog)
}

trait PublishingProgram[F[_]] extends Publishing[F] with Publish[F]

case class WrapperPublishingProgram[F[_]: Sync] private[program] (
    publish: Publish[F]
) extends PublishingProgram[F] {
  override def createPublisher[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit encoder: MessageEncoder[F, A]): F[A => F[Unit]] =
    createRoutingPublisher(channel, exchangeName).map(_.apply(routingKey))

  override def createPublisherWithListener[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit encoder: MessageEncoder[F, A]): F[A => F[Unit]] =
    createRoutingPublisherWithListener(channel, exchangeName, flag, listener)
      .map(_.apply(routingKey))

  override def createRoutingPublisher[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName
  )(implicit encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]] =
    createBasicPublisher(channel).map(pub => key => msg => pub(exchangeName, key, msg))

  override def createRoutingPublisherWithListener[A](
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]] =
    createBasicPublisherWithListener(channel, flag, listener).map(pub => key => msg => pub(exchangeName, key, msg))

  override def createBasicPublisher[A](
      channel: AMQPChannel
  )(implicit encoder: MessageEncoder[F, A]): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    Applicative[F].pure {
      case (
            exchangeName: ExchangeName,
            routingKey: RoutingKey,
            msg: A @unchecked
          ) =>
        encoder
          .run(msg)
          .flatMap(payload => publish.basicPublish(channel, exchangeName, routingKey, payload))
    }

  override def createBasicPublisherWithListener[A](
      channel: AMQPChannel,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit encoder: MessageEncoder[F, A]): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    publish.addPublishingListener(channel, listener).as {
      case (
            exchangeName: ExchangeName,
            routingKey: RoutingKey,
            msg: A @unchecked
          ) =>
        encoder
          .run(msg)
          .flatMap(payload =>
            publish.basicPublishWithFlag(
              channel,
              exchangeName,
              routingKey,
              flag,
              payload
            )
          )
    }

  override def basicPublish(
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      msg: AmqpMessage[Array[Byte]]
  ): F[Unit] =
    publish.basicPublish(channel, exchangeName, routingKey, msg)

  override def basicPublishWithFlag(
      channel: AMQPChannel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      msg: AmqpMessage[Array[Byte]]
  ): F[Unit] =
    publish.basicPublishWithFlag(channel, exchangeName, routingKey, flag, msg)

  override def addPublishingListener(channel: AMQPChannel, listener: PublishReturn => F[Unit]): F[Unit] =
    publish.addPublishingListener(channel, listener)

  override def clearPublishingListeners(channel: AMQPChannel): F[Unit] =
    publish.clearPublishingListeners(channel)
}
