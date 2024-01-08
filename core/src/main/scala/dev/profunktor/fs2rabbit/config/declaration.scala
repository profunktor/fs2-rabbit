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

import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName}

object declaration {

  final case class DeclarationQueueConfig(
      queueName: QueueName,
      durable: DurableCfg,
      exclusive: ExclusiveCfg,
      autoDelete: AutoDeleteCfg,
      arguments: Arguments
  )
  object DeclarationQueueConfig {

    def default(queueName: QueueName): DeclarationQueueConfig =
      DeclarationQueueConfig(queueName, NonDurable, NonExclusive, NonAutoDelete, Map.empty)
  }

  sealed trait DurableCfg extends Product with Serializable
  case object Durable     extends DurableCfg
  case object NonDurable  extends DurableCfg

  sealed trait ExclusiveCfg extends Product with Serializable
  case object Exclusive     extends ExclusiveCfg
  case object NonExclusive  extends ExclusiveCfg

  sealed trait AutoDeleteCfg extends Product with Serializable
  case object AutoDelete     extends AutoDeleteCfg
  case object NonAutoDelete  extends AutoDeleteCfg

  final case class DeclarationExchangeConfig(
      exchangeName: ExchangeName,
      exchangeType: ExchangeType,
      durable: DurableCfg,
      autoDelete: AutoDeleteCfg,
      internal: InternalCfg,
      arguments: Arguments
  )

  object DeclarationExchangeConfig {

    def default(exchangeName: ExchangeName, exchangeType: ExchangeType): DeclarationExchangeConfig =
      DeclarationExchangeConfig(exchangeName, exchangeType, NonDurable, NonAutoDelete, NonInternal, Map.empty)
  }

  sealed trait InternalCfg extends Product with Serializable
  case object Internal     extends InternalCfg
  case object NonInternal  extends InternalCfg

}
