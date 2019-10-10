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

import cats.effect.Sync
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.algebra.Deletion
import dev.profunktor.fs2rabbit.config.deletion
import dev.profunktor.fs2rabbit.config.deletion.DeletionQueueConfig
import dev.profunktor.fs2rabbit.effects.BoolValue.syntax._
import dev.profunktor.fs2rabbit.model.AMQPChannel

// TODO delete
object DeletionEffect {
  def apply[F[_]: Sync]: Deletion[F] = new DeletionEffect[F] {
    override lazy val sync: Sync[F] = Sync[F]
  }
}

trait DeletionEffect[F[_]] extends Deletion[F] {

  implicit val sync: Sync[F]

  override def deleteQueue(channel: AMQPChannel, config: DeletionQueueConfig): F[Unit] =
    Sync[F].delay {
      channel.value.queueDelete(
        config.queueName.value,
        config.ifUnused.isTrue,
        config.ifEmpty.isTrue
      )
    }.void

  override def deleteQueueNoWait(channel: AMQPChannel, config: DeletionQueueConfig): F[Unit] =
    Sync[F].delay {
      channel.value.queueDeleteNoWait(
        config.queueName.value,
        config.ifUnused.isTrue,
        config.ifEmpty.isTrue
      )
    }.void

  override def deleteExchange(
      channel: AMQPChannel,
      config: deletion.DeletionExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.value.exchangeDelete(config.exchangeName.value, config.ifUnused.isTrue)
    }.void

  override def deleteExchangeNoWait(
      channel: AMQPChannel,
      config: deletion.DeletionExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.value.exchangeDeleteNoWait(
        config.exchangeName.value,
        config.ifUnused.isTrue
      )
    }.void

}
