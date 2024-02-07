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

package dev.profunktor.fs2rabbit.config

import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName}

object deletion {

  final case class DeletionQueueConfig(
      queueName: QueueName,
      ifUnused: IfUnusedCfg,
      ifEmpty: IfEmptyCfg
  )

  object DeletionQueueConfig {
    def default(queueName: QueueName): DeletionQueueConfig =
      DeletionQueueConfig(queueName, Unused, Empty)
  }

  final case class DeletionExchangeConfig(exchangeName: ExchangeName, ifUnused: IfUnusedCfg)

  object DeletionExchangeConfig {
    def default(exchangeName: ExchangeName): DeletionExchangeConfig =
      DeletionExchangeConfig(exchangeName, Unused)
  }

  sealed trait IfEmptyCfg extends Product with Serializable
  case object Empty       extends IfEmptyCfg
  case object NonEmpty    extends IfEmptyCfg

  sealed trait IfUnusedCfg extends Product with Serializable
  case object Unused       extends IfUnusedCfg
  case object Used         extends IfUnusedCfg
}
