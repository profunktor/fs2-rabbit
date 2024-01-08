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
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.model._

private[fs2rabbit] final class DeclarationOps[F[_]](val declaration: Declaration[F]) extends AnyVal {
  def mapK[G[_]](fK: F ~> G): Declaration[G] = new Declaration[G] {
    def declareExchange(channel: AMQPChannel, exchangeConfig: DeclarationExchangeConfig): G[Unit] =
      fK(declaration.declareExchange(channel, exchangeConfig))

    def declareExchangeNoWait(value: AMQPChannel, exchangeConfig: DeclarationExchangeConfig): G[Unit] =
      fK(declaration.declareExchangeNoWait(value, exchangeConfig))

    def declareExchangePassive(channel: AMQPChannel, exchangeName: ExchangeName): G[Unit] =
      fK(declaration.declareExchangePassive(channel, exchangeName))

    def declareQueue(channel: AMQPChannel): G[QueueName] =
      fK(declaration.declareQueue(channel))

    def declareQueue(channel: AMQPChannel, queueConfig: DeclarationQueueConfig): G[Unit] =
      fK(declaration.declareQueue(channel, queueConfig))

    def declareQueueNoWait(channel: AMQPChannel, queueConfig: DeclarationQueueConfig): G[Unit] =
      fK(declaration.declareQueueNoWait(channel, queueConfig))

    def declareQueuePassive(channel: AMQPChannel, queueName: QueueName): G[Unit] =
      fK(declaration.declareQueuePassive(channel, queueName))
  }
}
