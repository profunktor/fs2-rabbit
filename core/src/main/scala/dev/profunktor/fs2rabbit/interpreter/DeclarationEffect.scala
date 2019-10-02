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

package dev.profunktor.fs2rabbit.interpreter

import cats.effect.{Effect, Sync}
import cats.syntax.functor._
import com.rabbitmq.client.Channel
import dev.profunktor.fs2rabbit.algebra.Declaration
import dev.profunktor.fs2rabbit.arguments._
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.effects.BoolValue.syntax._
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName}

object DeclarationEffect {
  def apply[F[_]: Effect]: Declaration[F] =
    new DeclarationEffect[F] {
      override lazy val effect: Effect[F] = Effect[F]
    }
}

trait DeclarationEffect[F[_]] extends Declaration[F] {

  implicit val effect: Effect[F]

  override def declareExchange(channel: Channel, config: DeclarationExchangeConfig): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeclare(
        config.exchangeName.value,
        config.exchangeType.toString.toLowerCase,
        config.durable.isTrue,
        config.autoDelete.isTrue,
        config.internal.isTrue,
        config.arguments
      )
    }.void

  override def declareExchangeNoWait(
      channel: Channel,
      config: DeclarationExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeclareNoWait(
        config.exchangeName.value,
        config.exchangeType.toString.toLowerCase,
        config.durable.isTrue,
        config.autoDelete.isTrue,
        config.internal.isTrue,
        config.arguments
      )
    }.void

  override def declareExchangePassive(channel: Channel, exchangeName: ExchangeName): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeclarePassive(exchangeName.value)
    }.void

  override def declareQueue(channel: Channel): F[QueueName] =
    Sync[F].delay {
      QueueName(channel.queueDeclare().getQueue)
    }

  override def declareQueue(channel: Channel, config: DeclarationQueueConfig): F[Unit] =
    Sync[F].delay {
      channel.queueDeclare(
        config.queueName.value,
        config.durable.isTrue,
        config.exclusive.isTrue,
        config.autoDelete.isTrue,
        config.arguments
      )
    }.void

  override def declareQueueNoWait(channel: Channel, config: DeclarationQueueConfig): F[Unit] =
    Sync[F].delay {
      channel.queueDeclareNoWait(
        config.queueName.value,
        config.durable.isTrue,
        config.exclusive.isTrue,
        config.autoDelete.isTrue,
        config.arguments
      )
    }.void

  override def declareQueuePassive(channel: Channel, queueName: QueueName): F[Unit] =
    Sync[F].delay {
      channel.queueDeclarePassive(queueName.value)
    }.void

}
