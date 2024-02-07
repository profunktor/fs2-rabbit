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
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.model._
import fs2.Stream

final class AckConsumingProgramOps[F[_]](val prog: AckConsumingProgram[F]) extends AnyVal {
  def imapK[G[_]](fk: F ~> G)(gk: G ~> F)(implicit F: Functor[F]): AckConsumingProgram[G] =
    new AckConsumingProgram[G] {
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
        fk(
          prog
            .createConsumer[A](queueName, channel, basicQos, autoAck, noLocal, exclusive, consumerTag, args)(
              decoder.mapK(gk)
            )
            .map(_.translate(fk))
        )

      def createAcker(channel: AMQPChannel, ackMultiple: AckMultiple): G[AckResult => G[Unit]] =
        fk(prog.createAcker(channel, ackMultiple).map(_.andThen(fk.apply)))

      def createAckerConsumer[A](
          channel: AMQPChannel,
          queueName: QueueName,
          basicQos: BasicQos,
          consumerArgs: Option[ConsumerArgs],
          ackMultiple: AckMultiple
      )(implicit decoder: EnvelopeDecoder[G, A]): G[(AckResult => G[Unit], Stream[G, AmqpEnvelope[A]])] =
        fk(
          prog
            .createAckerConsumer[A](channel, queueName, basicQos, consumerArgs, ackMultiple)(
              decoder.mapK(gk)
            )
            .map { case (acker, stream) =>
              (acker.andThen(fk.apply), stream.translate(fk))
            }
        )

      def createAutoAckConsumer[A](
          channel: AMQPChannel,
          queueName: QueueName,
          basicQos: BasicQos,
          consumerArgs: Option[ConsumerArgs]
      )(implicit decoder: EnvelopeDecoder[G, A]): G[Stream[G, AmqpEnvelope[A]]] =
        fk(
          prog
            .createAutoAckConsumer[A](channel, queueName, basicQos, consumerArgs)(
              decoder.mapK(gk)
            )
            .map(_.translate(fk))
        )

      def basicCancel(channel: AMQPChannel, consumerTag: ConsumerTag): G[Unit] =
        fk(prog.basicCancel(channel, consumerTag))

      def createAckerWithMultipleFlag(channel: AMQPChannel): G[(AckResult, AckMultiple) => G[Unit]] =
        fk(prog.createAckerWithMultipleFlag(channel).map { acker =>
          { case (result, flag) =>
            fk(acker(result, flag))
          }
        })

      def createAckerConsumerWithMultipleFlag[A](
          channel: AMQPChannel,
          queueName: QueueName,
          basicQos: BasicQos,
          consumerArgs: Option[ConsumerArgs]
      )(implicit decoder: EnvelopeDecoder[G, A]): G[((AckResult, AckMultiple) => G[Unit], Stream[G, AmqpEnvelope[A]])] =
        fk(
          prog
            .createAckerConsumerWithMultipleFlag[A](channel, queueName, basicQos, consumerArgs)(
              decoder.mapK(gk)
            )
            .map { case (acker, stream) =>
              val gAcker: (AckResult, AckMultiple) => G[Unit] = { case (result, flag) => fk(acker(result, flag)) }
              (gAcker, stream.translate(fk))
            }
        )
    }
}
