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

import cats.{Applicative, Apply}
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.{AckConsuming, Acking, InternalQueue}
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.interpreter.ConsumeEffect
import cats.effect._
import dev.profunktor.fs2rabbit.model._
import com.rabbitmq.client.Channel
import dev.profunktor.fs2rabbit.algebra.ConsumingStream.ConsumingStream
import fs2.Stream
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig

object AckConsumingProgram {
  def apply[F[_]: Applicative: Effect: Bracket[?[_], Throwable]](
      configuration: Fs2RabbitConfig,
      internalQueue: InternalQueue[F]): AckConsumingProgram[F] =
    new AckConsumingProgram[F] with ConsumeEffect[F] with AckingProgram[F] with ConsumingProgram[F] {
      override lazy val applicative: Applicative[F]    = Applicative[F]
      override lazy val apply: Apply[F]                = applicative
      override lazy val effect: Effect[F]              = Effect[F]
      override lazy val config: Fs2RabbitConfig        = configuration
      override lazy val IQ: InternalQueue[F]           = internalQueue
      override lazy val bracket: Bracket[F, Throwable] = Bracket[F, Throwable]
    }
}

trait AckConsumingProgram[F[_]] extends AckConsuming[F, Stream[F, ?]] { this: Acking[F] with ConsumingStream[F] =>
  implicit val apply: Apply[F]

  override def createAckerConsumer[A](
      channel: Channel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): F[(AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] = {
    val makeConsumer =
      consumerArgs.fold(this.createConsumer(queueName, channel, basicQos)) { args =>
        this.createConsumer[A](
          queueName = queueName,
          channel = channel,
          basicQos = basicQos,
          noLocal = args.noLocal,
          exclusive = args.exclusive,
          consumerTag = args.consumerTag,
          args = args.args
        )
      }
    (this.createAcker(channel), makeConsumer).tupled
  }

  override def createAutoAckConsumer[A](
      channel: Channel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] =
    consumerArgs.fold(this.createConsumer(queueName, channel, basicQos, autoAck = true)) { args =>
      this.createConsumer[A](
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

}
