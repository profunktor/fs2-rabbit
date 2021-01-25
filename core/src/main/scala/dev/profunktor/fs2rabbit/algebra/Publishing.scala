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

import cats.{Functor, ~>}
import cats.syntax.functor._
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

object Publishing {
  private[fs2rabbit] implicit class PublishingOps[F[_]](val publish: Publishing[F]) extends AnyVal {
    def imapK[G[_]: Functor](af: F ~> G)(ag: G ~> F): Publishing[G] = new Publishing[G] {
      def createPublisher[A](
          channel: AMQPChannel,
          exchangeName: ExchangeName,
          routingKey: RoutingKey
      )(implicit encoder: MessageEncoder[G, A]): G[A => G[Unit]] =
        af(publish.createPublisher(channel, exchangeName, routingKey)(encoder.mapK(ag): MessageEncoder[F, A]))
          .map(_.andThen(af.apply))

      def createPublisherWithListener[A](
          channel: AMQPChannel,
          exchangeName: ExchangeName,
          routingKey: RoutingKey,
          flags: PublishingFlag,
          listener: PublishReturn => G[Unit]
      )(implicit encoder: MessageEncoder[G, A]): G[A => G[Unit]] =
        af(
          publish.createPublisherWithListener(channel, exchangeName, routingKey, flags, listener.andThen(ag.apply))(
            encoder.mapK(ag): MessageEncoder[F, A]))
          .map(_.andThen(af.apply))

      def createRoutingPublisher[A](
          channel: AMQPChannel,
          exchangeName: ExchangeName
      )(implicit encoder: MessageEncoder[G, A]): G[RoutingKey => A => G[Unit]] =
        af(publish.createRoutingPublisher(channel, exchangeName)(encoder.mapK(ag): MessageEncoder[F, A]))
          .map(_.andThen(_.andThen(af.apply)))

      def createRoutingPublisherWithListener[A](
          channel: AMQPChannel,
          exchangeName: ExchangeName,
          flags: PublishingFlag,
          listener: PublishReturn => G[Unit]
      )(implicit encoder: MessageEncoder[G, A]): G[RoutingKey => A => G[Unit]] =
        af(
          publish.createRoutingPublisherWithListener(channel, exchangeName, flags, listener.andThen(ag.apply))(
            encoder.mapK(ag): MessageEncoder[F, A]))
          .map(_.andThen(_.andThen(af.apply)))

      def createBasicPublisher[A](channel: AMQPChannel)(
          implicit encoder: MessageEncoder[G, A]): G[(ExchangeName, RoutingKey, A) => G[Unit]] =
        af(publish.createBasicPublisher(channel)(encoder.mapK(ag): MessageEncoder[F, A]))
          .map(f => {
            case (e, r, a) =>
              af(f(e, r, a))
          })

      def createBasicPublisherWithListener[A](
          channel: AMQPChannel,
          flags: PublishingFlag,
          listener: PublishReturn => G[Unit]
      )(implicit encoder: MessageEncoder[G, A]): G[(ExchangeName, RoutingKey, A) => G[Unit]] =
        af(
          publish.createBasicPublisherWithListener(channel, flags, listener.andThen(ag.apply))(
            encoder.mapK(ag): MessageEncoder[F, A]))
          .map(f => {
            case (e, r, a) =>
              af(f(e, r, a))
          })
    }
  }
}
