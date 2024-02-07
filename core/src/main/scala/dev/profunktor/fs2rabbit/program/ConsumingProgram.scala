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

import cats.effect.Sync
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.ConsumingStream._
import dev.profunktor.fs2rabbit.algebra.{AMQPInternals, Consume, InternalQueue}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.model._
import fs2.Stream

object ConsumingProgram {
  def make[F[_]: Sync](internalQueue: InternalQueue[F], consume: Consume[F]): ConsumingProgram[F] =
    WrapperConsumingProgram(internalQueue, consume)
}

trait ConsumingProgram[F[_]] extends ConsumingStream[F] with Consume[F]

case class WrapperConsumingProgram[F[_]: Sync] private[program] (
    internalQueue: InternalQueue[F],
    consume: Consume[F]
) extends ConsumingProgram[F] {

  override def createConsumer[A](
      queueName: QueueName,
      channel: AMQPChannel,
      basicQos: BasicQos,
      autoAck: Boolean = false,
      noLocal: Boolean = false,
      exclusive: Boolean = false,
      consumerTag: ConsumerTag = ConsumerTag(""),
      args: Arguments = Map.empty
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] = {

    val setup = for {
      internalQ   <- internalQueue.create
      internals    = AMQPInternals[F](Some(internalQ))
      _           <- consume.basicQos(channel, basicQos)
      consumerTag <- consume.basicConsume(
                       channel,
                       queueName,
                       autoAck,
                       consumerTag,
                       noLocal,
                       exclusive,
                       args
                     )(internals)
    } yield (consumerTag, internalQ)

    Stream
      .bracket(setup) { case (tag, _) =>
        consume.basicCancel(channel, tag)
      }
      .flatMap { case (_, queue) =>
        Stream
          .fromQueueUnterminated(queue)
          .rethrow
          .evalMap(env => decoder(env).map(a => env.copy(payload = a)))
      }
      .pure[F]
  }

  override def basicAck(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean): F[Unit] =
    consume.basicAck(channel, tag, multiple)

  override def basicNack(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    consume.basicNack(channel, tag, multiple, requeue)

  override def basicReject(channel: AMQPChannel, tag: DeliveryTag, requeue: Boolean): F[Unit] =
    consume.basicReject(channel, tag, requeue)

  override def basicQos(channel: AMQPChannel, basicQos: BasicQos): F[Unit] =
    consume.basicQos(channel, basicQos)

  override def basicConsume[A](
      channel: AMQPChannel,
      queueName: QueueName,
      autoAck: Boolean,
      consumerTag: ConsumerTag,
      noLocal: Boolean,
      exclusive: Boolean,
      args: Arguments
  )(internals: AMQPInternals[F]): F[ConsumerTag] =
    consume.basicConsume(channel, queueName, autoAck, consumerTag, noLocal, exclusive, args)(internals)

  override def basicCancel(channel: AMQPChannel, consumerTag: ConsumerTag): F[Unit] =
    consume.basicCancel(channel, consumerTag)
}
