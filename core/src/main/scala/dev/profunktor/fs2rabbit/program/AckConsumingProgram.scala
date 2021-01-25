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

package dev.profunktor.fs2rabbit.program

import cats.{Functor, ~>}
import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.ConsumingStream.ConsumingStream
import dev.profunktor.fs2rabbit.algebra.{AckConsuming, Acking, InternalQueue}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.model._
import fs2.Stream

object AckConsumingProgram {
  def make[F[_]: Sync](configuration: Fs2RabbitConfig,
                       internalQueue: InternalQueue[F],
                       dispatcher: Dispatcher[F]): F[AckConsumingProgram[F]] =
    (AckingProgram.make(configuration, dispatcher), ConsumingProgram.make(internalQueue, dispatcher)).mapN {
      case (ap, cp) =>
        WrapperAckConsumingProgram(ap, cp)
    }

  private[fs2rabbit] implicit class AckConsumingStreamOps[F[_]](val stream: AckConsumingProgram[F]) extends AnyVal {
    def imapK[G[_]: Functor](af: F ~> G)(ag: G ~> F): AckConsumingProgram[G] = new AckConsumingProgram[G] {
      def createAckerConsumer[A](
          channel: AMQPChannel,
          queueName: QueueName,
          basicQos: BasicQos,
          consumerArgs: Option[ConsumerArgs]
      )(implicit decoder: EnvelopeDecoder[G, A]): G[(AckResult => G[Unit], Stream[G, AmqpEnvelope[A]])] =
        af(stream.createAckerConsumer(channel, queueName, basicQos, consumerArgs)(decoder.mapK(ag))).map {
          case (ackFunction, envelopeStream) =>
            (ackFunction.andThen(af.apply), envelopeStream.translate(af))
        }

      def createAutoAckConsumer[A](
          channel: AMQPChannel,
          queueName: QueueName,
          basicQos: BasicQos,
          consumerArgs: Option[ConsumerArgs]
      )(implicit decoder: EnvelopeDecoder[G, A]): G[Stream[G, AmqpEnvelope[A]]] =
        af(stream.createAutoAckConsumer(channel, queueName, basicQos, consumerArgs)(decoder.mapK(ag)))
          .map(_.translate(af))

      def createConsumer[A](
          queueName: QueueName,
          channel: AMQPChannel,
          basicQos: BasicQos,
          autoAck: Boolean,
          noLocal: Boolean,
          exclusive: Boolean,
          consumerTag: ConsumerTag,
          args: Arguments
      )(implicit decoder: EnvelopeDecoder[G, A]): G[Stream[G, AmqpEnvelope[A]]] =
        af(
          stream.createConsumer(queueName, channel, basicQos, autoAck, noLocal, exclusive, consumerTag, args)(
            decoder.mapK(ag))).map(_.translate(af))

      def createAcker(channel: AMQPChannel): G[AckResult => G[Unit]] =
        af(stream.createAcker(channel)).map(_.andThen(af.apply))
    }
  }
}

trait AckConsumingProgram[F[_]] extends AckConsuming[F, Stream[F, *]] with Acking[F] with ConsumingStream[F]

case class WrapperAckConsumingProgram[F[_]: Sync] private (
    ackingProgram: AckingProgram[F],
    consumingProgram: ConsumingProgram[F]
) extends AckConsumingProgram[F] {

  override def createAckerConsumer[A](
      channel: AMQPChannel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): F[(AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] = {
    val makeConsumer =
      consumerArgs.fold(consumingProgram.createConsumer(queueName, channel, basicQos)) { args =>
        consumingProgram.createConsumer[A](
          queueName = queueName,
          channel = channel,
          basicQos = basicQos,
          noLocal = args.noLocal,
          exclusive = args.exclusive,
          consumerTag = args.consumerTag,
          args = args.args
        )
      }
    (ackingProgram.createAcker(channel), makeConsumer).tupled
  }

  override def createAutoAckConsumer[A](
      channel: AMQPChannel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] =
    consumerArgs.fold(consumingProgram.createConsumer(queueName, channel, basicQos, autoAck = true)) { args =>
      consumingProgram.createConsumer[A](
        queueName = queueName,
        channel = channel,
        basicQos = basicQos,
        autoAck = true,
        noLocal = args.noLocal,
        exclusive = args.exclusive,
        consumerTag = args.consumerTag,
        args = args.args
      )
    }

  override def createConsumer[A](
      queueName: QueueName,
      channel: AMQPChannel,
      basicQos: BasicQos,
      autoAck: Boolean,
      noLocal: Boolean,
      exclusive: Boolean,
      consumerTag: ConsumerTag,
      args: Arguments)(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] =
    consumingProgram.createConsumer(queueName, channel, basicQos, autoAck, noLocal, exclusive, consumerTag, args)

  override def createAcker(channel: AMQPChannel): F[AckResult => F[Unit]] =
    ackingProgram.createAcker(channel)
}
