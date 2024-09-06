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

import cats.{Functor, ~>}
import cats.implicits._
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model._

final class PublishingProgramOps[F[_]](val prog: PublishingProgram[F]) extends AnyVal {
  def imapK[G[_]](fk: F ~> G)(gk: G ~> F)(implicit F: Functor[F]): PublishingProgram[G] = new PublishingProgram[G] {
    def createPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
        encoder: MessageEncoder[G, A]
    ): G[A => G[Unit]] =
      fk(
        prog.createPublisher[A](channel, exchangeName, routingKey)(encoder.mapK(gk)).map(_.andThen(fk.apply))
      )

    def createPublisherWithListener[A](
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flags: PublishingFlag,
        listener: PublishReturn => G[Unit]
    )(implicit encoder: MessageEncoder[G, A]): G[A => G[Unit]] =
      fk(
        prog
          .createPublisherWithListener[A](channel, exchangeName, routingKey, flags, listener.andThen(gk.apply))(
            encoder.mapK(gk)
          )
          .map(_.andThen(fk.apply))
      )

    def createRoutingPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName)(implicit
        encoder: MessageEncoder[G, A]
    ): G[RoutingKey => A => G[Unit]] =
      fk(
        prog.createRoutingPublisher[A](channel, exchangeName)(encoder.mapK(gk)).map {
          f => (routingKey: RoutingKey) => (a: A) =>
            fk(f(routingKey)(a))
        }
      )

    def createRoutingPublisherWithListener[A](
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        flags: PublishingFlag,
        listener: PublishReturn => G[Unit]
    )(implicit encoder: MessageEncoder[G, A]): G[RoutingKey => A => G[Unit]] =
      fk(
        prog
          .createRoutingPublisherWithListener[A](channel, exchangeName, flags, listener.andThen(gk.apply))(
            encoder.mapK(gk)
          )
          .map { f => (routingKey: RoutingKey) => (a: A) =>
            fk(f(routingKey)(a))
          }
      )

    def createBasicPublisher[A](
        channel: AMQPChannel
    )(implicit encoder: MessageEncoder[G, A]): G[(ExchangeName, RoutingKey, A) => G[Unit]] =
      fk(prog.createBasicPublisher[A](channel)(encoder.mapK(gk)).map { f =>
        { case (e, r, a) =>
          fk(f(e, r, a))
        }
      })

    def createBasicPublisherWithListener[A](
        channel: AMQPChannel,
        flags: PublishingFlag,
        listener: PublishReturn => G[Unit]
    )(implicit encoder: MessageEncoder[G, A]): G[(ExchangeName, RoutingKey, A) => G[Unit]] =
      fk(
        prog
          .createBasicPublisherWithListener[A](channel, flags, listener.andThen(gk.apply))(encoder.mapK(gk))
          .map { f =>
            { case (e, r, a) =>
              fk(f(e, r, a))
            }
          }
      )

    def basicPublish(
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        msg: AmqpMessage[Array[Byte]]
    ): G[Unit] =
      fk(prog.basicPublish(channel, exchangeName, routingKey, msg))

    def basicPublishWithFlag(
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flag: PublishingFlag,
        msg: AmqpMessage[Array[Byte]]
    ): G[Unit] = fk(prog.basicPublishWithFlag(channel, exchangeName, routingKey, flag, msg))

    def addPublishingListener(channel: AMQPChannel, listener: PublishReturn => G[Unit]): G[Unit] =
      fk(prog.addPublishingListener(channel, listener.andThen(gk.apply)))

    def clearPublishingListeners(channel: AMQPChannel): G[Unit] =
      fk(prog.clearPublishingListeners(channel))
  }
}
