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
import dev.profunktor.fs2rabbit.algebra.{AMQPInternals, Acking, Consume}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.model.AckResult.{Ack, NAck, Reject}
import dev.profunktor.fs2rabbit.model._

object AckingProgram {
  def make[F[_]: Sync](config: Fs2RabbitConfig, dispatcher: Dispatcher[F]): AckingProgram[F] =
    WrapperAckingProgram(config, Consume.make(dispatcher))
}

trait AckingProgram[F[_]] extends Acking[F] with Consume[F]

case class WrapperAckingProgram[F[_]: Sync] private[program] (
    config: Fs2RabbitConfig,
    consume: Consume[F]
) extends AckingProgram[F] {
  override def createAcker(channel: AMQPChannel, multiple: AckMultiple): F[AckResult => F[Unit]] = Applicative[F].pure {
    ackResult =>
      _createAckerWithMultipleFlag(channel)(ackResult, multiple)
  }

  override def createAckerWithMultipleFlag(channel: AMQPChannel): F[(AckResult, AckMultiple) => F[Unit]] =
    Applicative[F].pure {
      _createAckerWithMultipleFlag(channel)
    }

  private def _createAckerWithMultipleFlag(channel: AMQPChannel): (AckResult, AckMultiple) => F[Unit] = {
    case (Ack(tag), flag)  => consume.basicAck(channel, tag, multiple = flag.multiple)
    case (NAck(tag), flag) =>
      consume.basicNack(channel, tag, multiple = flag.multiple, config.requeueOnNack)
    case (Reject(tag), _)  =>
      consume.basicReject(channel, tag, config.requeueOnReject)
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
