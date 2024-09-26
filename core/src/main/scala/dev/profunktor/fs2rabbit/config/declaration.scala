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

import dev.profunktor.fs2rabbit.arguments.{Arguments, SafeArg}
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, QueueType}

object declaration {

  // ----- Queue Config -----
  final case class DeclarationQueueConfig(
      queueName: QueueName,
      durable: DurableCfg,
      exclusive: ExclusiveCfg,
      autoDelete: AutoDeleteCfg,
      arguments: Arguments,
      queueType: Option[QueueType]
  ) {

    lazy val validatedArguments: Either[IllegalArgumentException, Arguments] =
      queueType match {
        case Some(_) if arguments.contains("x-queue-type") =>
          Left(
            new IllegalArgumentException(
              "Queue type defined twice. It is set in the arguments and in the DeclarationQueueConfig."
            )
          )
        case Some(queueType)                               =>
          Right(arguments + ("x-queue-type" -> queueType.asString))
        case None                                          =>
          Right(arguments)
      }

    // arguments
    def withArguments(arguments: Arguments): DeclarationQueueConfig =
      copy(arguments = arguments)

    def withArguments(arguments: (String, SafeArg)*): DeclarationQueueConfig =
      withArguments(arguments.toMap)

    // durable
    def withDurable: DeclarationQueueConfig =
      copy(durable = Durable)

    def withNonDurable: DeclarationQueueConfig =
      copy(durable = NonDurable)

    // autoDelete
    def withAutoDelete: DeclarationQueueConfig =
      copy(autoDelete = AutoDelete)

    def withNonAutoDelete: DeclarationQueueConfig =
      copy(autoDelete = NonAutoDelete)

    // exclusive
    def withExclusive: DeclarationQueueConfig =
      copy(exclusive = Exclusive)

    def withNonExclusive: DeclarationQueueConfig =
      copy(exclusive = NonExclusive)

  }
  object DeclarationQueueConfig {

    def default(queueName: QueueName): DeclarationQueueConfig =
      DeclarationQueueConfig(
        queueName = queueName,
        durable = NonDurable,
        exclusive = NonExclusive,
        autoDelete = NonAutoDelete,
        arguments = Map.empty,
        queueType = None
      )

    def classic(queueName: QueueName): DeclarationQueueConfig =
      default(queueName).copy(queueType = Some(QueueType.Classic))

    def quorum(queueName: QueueName): DeclarationQueueConfig =
      default(queueName).copy(queueType = Some(QueueType.Quorum))

    def stream(queueName: QueueName): DeclarationQueueConfig =
      default(queueName).copy(queueType = Some(QueueType.Stream))
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

  // ----- Exchange Config -----
  final case class DeclarationExchangeConfig(
      exchangeName: ExchangeName,
      exchangeType: ExchangeType,
      durable: DurableCfg,
      autoDelete: AutoDeleteCfg,
      internal: InternalCfg,
      arguments: Arguments
  ) {

    // arguments
    def withArguments(arguments: Arguments): DeclarationExchangeConfig =
      copy(arguments = arguments)

    def withArguments(arguments: (String, SafeArg)*): DeclarationExchangeConfig =
      withArguments(arguments.toMap)

    // durable
    def withDurable: DeclarationExchangeConfig =
      copy(durable = Durable)

    def withNonDurable: DeclarationExchangeConfig =
      copy(durable = NonDurable)

    // autoDelete
    def withAutoDelete: DeclarationExchangeConfig =
      copy(autoDelete = AutoDelete)

    def withNonAutoDelete: DeclarationExchangeConfig =
      copy(autoDelete = NonAutoDelete)

    // internal
    def withInternal: DeclarationExchangeConfig =
      copy(internal = Internal)

    def withNonInternal: DeclarationExchangeConfig =
      copy(internal = NonInternal)
  }

  object DeclarationExchangeConfig {

    def default(exchangeName: ExchangeName, exchangeType: ExchangeType): DeclarationExchangeConfig =
      DeclarationExchangeConfig(
        exchangeName = exchangeName,
        exchangeType = exchangeType,
        durable = NonDurable,
        autoDelete = NonAutoDelete,
        internal = NonInternal,
        arguments = Map.empty
      )
  }

  sealed trait InternalCfg extends Product with Serializable
  case object Internal     extends InternalCfg
  case object NonInternal  extends InternalCfg

}
