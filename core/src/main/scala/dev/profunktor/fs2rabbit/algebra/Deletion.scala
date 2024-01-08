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

package dev.profunktor.fs2rabbit.algebra

import cats.effect.Sync
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.config.deletion
import dev.profunktor.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import dev.profunktor.fs2rabbit.effects.BoolValue.syntax._
import dev.profunktor.fs2rabbit.model.AMQPChannel

object Deletion {
  def make[F[_]: Sync]: Deletion[F] = new Deletion[F] {
    override def deleteQueue(channel: AMQPChannel, config: DeletionQueueConfig): F[Unit] =
      Sync[F].blocking {
        channel.value.queueDelete(
          config.queueName.value,
          config.ifUnused.isTrue,
          config.ifEmpty.isTrue
        )
      }.void

    override def deleteQueueNoWait(channel: AMQPChannel, config: DeletionQueueConfig): F[Unit] =
      Sync[F].blocking {
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
      Sync[F].blocking {
        channel.value.exchangeDelete(config.exchangeName.value, config.ifUnused.isTrue)
      }.void

    override def deleteExchangeNoWait(
        channel: AMQPChannel,
        config: deletion.DeletionExchangeConfig
    ): F[Unit] =
      Sync[F].blocking {
        channel.value.exchangeDeleteNoWait(
          config.exchangeName.value,
          config.ifUnused.isTrue
        )
      }.void
  }

  implicit def toDeletionOps[F[_]](deletion: Deletion[F]): DeletionOps[F] = new DeletionOps[F](deletion)
}

trait Deletion[F[_]] {
  def deleteQueue(channel: AMQPChannel, config: DeletionQueueConfig): F[Unit]
  def deleteQueueNoWait(channel: AMQPChannel, config: DeletionQueueConfig): F[Unit]
  def deleteExchange(channel: AMQPChannel, config: DeletionExchangeConfig): F[Unit]
  def deleteExchangeNoWait(channel: AMQPChannel, config: DeletionExchangeConfig): F[Unit]
}
