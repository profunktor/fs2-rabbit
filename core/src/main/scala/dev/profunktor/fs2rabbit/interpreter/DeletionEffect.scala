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
import dev.profunktor.fs2rabbit.algebra.Deletion
import dev.profunktor.fs2rabbit.config.deletion
import dev.profunktor.fs2rabbit.config.deletion.DeletionQueueConfig
import dev.profunktor.fs2rabbit.effects.BoolValue.syntax._

object DeletionEffect {
  def apply[F[_]: Effect]: Deletion[F] = new DeletionEffect[F] {
    override lazy val effect: Effect[F] = Effect[F]
  }
}

trait DeletionEffect[F[_]] extends Deletion[F] {

  implicit val effect: Effect[F]

  override def deleteQueue(channel: Channel, config: DeletionQueueConfig): F[Unit] =
    Sync[F].delay {
      channel.queueDelete(
        config.queueName.value,
        config.ifUnused.isTrue,
        config.ifEmpty.isTrue
      )
    }.void

  override def deleteQueueNoWait(channel: Channel, config: DeletionQueueConfig): F[Unit] =
    Sync[F].delay {
      channel.queueDeleteNoWait(
        config.queueName.value,
        config.ifUnused.isTrue,
        config.ifEmpty.isTrue
      )
    }.void

  override def deleteExchange(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDelete(config.exchangeName.value, config.ifUnused.isTrue)
    }.void

  override def deleteExchangeNoWait(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeleteNoWait(
        config.exchangeName.value,
        config.ifUnused.isTrue
      )
    }.void

}
