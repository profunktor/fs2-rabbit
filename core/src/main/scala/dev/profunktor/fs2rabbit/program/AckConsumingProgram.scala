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

import cats.effect._
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.ConsumingStream.ConsumingStream
import dev.profunktor.fs2rabbit.algebra.{AckConsuming, Acking, InternalQueue}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.model._
import fs2.Stream

object AckConsumingProgram {
  def make[F[_]: Effect: ContextShift](configuration: Fs2RabbitConfig,
                                       internalQueue: InternalQueue[F],
                                       blocker: Blocker): F[AckConsumingProgram[F]] =
    (AckingProgram.make(configuration, blocker), ConsumingProgram.make(internalQueue, blocker)).mapN {
      case (ap, cp) =>
        WrapperAckConsumingProgram(ap, cp)
    }
}

trait AckConsumingProgram[F[_]] extends AckConsuming[F, Stream[F, *]] with Acking[F] with ConsumingStream[F]

case class WrapperAckConsumingProgram[F[_]: Effect] private (
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
