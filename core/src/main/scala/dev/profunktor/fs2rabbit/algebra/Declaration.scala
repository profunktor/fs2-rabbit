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

package dev.profunktor.fs2rabbit.algebra

import com.rabbitmq.client.Channel
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.model._

object Declaration {
  def apply[F[_]](implicit ev: Declaration[F]): Declaration[F] = ev
}

trait Declaration[F[_]] {
  def declareExchange(channel: Channel, exchangeConfig: DeclarationExchangeConfig): F[Unit]
  def declareExchangeNoWait(value: Channel, exchangeConfig: DeclarationExchangeConfig): F[Unit]
  def declareExchangePassive(channel: Channel, exchangeName: ExchangeName): F[Unit]
  def declareQueue(channel: Channel): F[QueueName]
  def declareQueue(channel: Channel, queueConfig: DeclarationQueueConfig): F[Unit]
  def declareQueueNoWait(channel: Channel, queueConfig: DeclarationQueueConfig): F[Unit]
  def declareQueuePassive(channel: Channel, queueName: QueueName): F[Unit]
}
