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

import cats.~>
import dev.profunktor.fs2rabbit.config.deletion._
import dev.profunktor.fs2rabbit.model._

private[fs2rabbit] final class DeletionOps[F[_]](val deletion: Deletion[F]) extends AnyVal {
  def mapK[G[_]](fK: F ~> G): Deletion[G] = new Deletion[G] {
    def deleteQueue(channel: AMQPChannel, config: DeletionQueueConfig): G[Unit] =
      fK(deletion.deleteQueue(channel, config))

    def deleteQueueNoWait(channel: AMQPChannel, config: DeletionQueueConfig): G[Unit] =
      fK(deletion.deleteQueueNoWait(channel, config))

    def deleteExchange(channel: AMQPChannel, config: DeletionExchangeConfig): G[Unit] =
      fK(deletion.deleteExchange(channel, config))

    def deleteExchangeNoWait(channel: AMQPChannel, config: DeletionExchangeConfig): G[Unit] =
      fK(deletion.deleteExchangeNoWait(channel, config))
  }
}
