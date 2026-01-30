/*
 * Copyright 2017-2026 ProfunKtor
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

import dev.profunktor.fs2rabbit.arguments.*
import dev.profunktor.fs2rabbit.config.declaration.*
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, QueueType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DeclarationConfigSpec extends AnyFlatSpec with Matchers {

  behavior of "DeclarationQueueConfig"

  it should "create default config with correct defaults" in {
    val config = DeclarationQueueConfig.default(QueueName("test-queue"))
    config.queueName shouldBe QueueName("test-queue")
    config.durable shouldBe NonDurable
    config.exclusive shouldBe NonExclusive
    config.autoDelete shouldBe NonAutoDelete
    config.arguments shouldBe empty
    config.queueType shouldBe None
  }

  it should "create classic queue config" in {
    val config = DeclarationQueueConfig.classic(QueueName("classic-queue"))
    config.queueType shouldBe Some(QueueType.Classic)
  }

  it should "create quorum queue config" in {
    val config = DeclarationQueueConfig.quorum(QueueName("quorum-queue"))
    config.queueType shouldBe Some(QueueType.Quorum)
  }

  it should "create stream queue config" in {
    val config = DeclarationQueueConfig.stream(QueueName("stream-queue"))
    config.queueType shouldBe Some(QueueType.Stream)
  }

  it should "set durable with withDurable" in {
    val config = DeclarationQueueConfig.default(QueueName("q")).withDurable
    config.durable shouldBe Durable
  }

  it should "set non-durable with withNonDurable" in {
    val config = DeclarationQueueConfig.default(QueueName("q")).withDurable.withNonDurable
    config.durable shouldBe NonDurable
  }

  it should "set exclusive with withExclusive" in {
    val config = DeclarationQueueConfig.default(QueueName("q")).withExclusive
    config.exclusive shouldBe Exclusive
  }

  it should "set non-exclusive with withNonExclusive" in {
    val config = DeclarationQueueConfig.default(QueueName("q")).withExclusive.withNonExclusive
    config.exclusive shouldBe NonExclusive
  }

  it should "set auto-delete with withAutoDelete" in {
    val config = DeclarationQueueConfig.default(QueueName("q")).withAutoDelete
    config.autoDelete shouldBe AutoDelete
  }

  it should "set non-auto-delete with withNonAutoDelete" in {
    val config = DeclarationQueueConfig.default(QueueName("q")).withAutoDelete.withNonAutoDelete
    config.autoDelete shouldBe NonAutoDelete
  }

  it should "set arguments with withArguments (Map)" in {
    val args: Arguments = Map("x-max-length" -> (1000: SafeArg))
    val config          = DeclarationQueueConfig.default(QueueName("q")).withArguments(args)
    config.arguments shouldBe args
  }

  it should "set arguments with withArguments (varargs)" in {
    val config = DeclarationQueueConfig.default(QueueName("q")).withArguments("x-max-length" -> (1000: SafeArg))
    config.arguments.keys should contain("x-max-length")
  }

  it should "chain multiple builder methods" in {
    val config = DeclarationQueueConfig
      .default(QueueName("q"))
      .withDurable
      .withExclusive
      .withAutoDelete
      .withArguments("x-max-length" -> (500: SafeArg))

    config.durable shouldBe Durable
    config.exclusive shouldBe Exclusive
    config.autoDelete shouldBe AutoDelete
    config.arguments.keys should contain("x-max-length")
  }

  it should "validate arguments when queueType is set via config" in {
    val config = DeclarationQueueConfig.quorum(QueueName("q"))
    val result = config.validatedArguments
    result shouldBe a[Right[_, _]]
    result.toOption.get.keys should contain("x-queue-type")
  }

  it should "validate arguments when queueType is not set" in {
    val config = DeclarationQueueConfig.default(QueueName("q"))
    config.validatedArguments shouldBe Right(Map.empty)
  }

  it should "fail validation when x-queue-type is set both in config and arguments" in {
    val config = DeclarationQueueConfig
      .quorum(QueueName("q"))
      .withArguments("x-queue-type" -> ("classic": SafeArg))

    val result = config.validatedArguments
    result shouldBe a[Left[_, _]]
    result.left.toOption.get shouldBe a[IllegalArgumentException]
  }

  it should "allow x-queue-type in arguments when queueType is None" in {
    val config = DeclarationQueueConfig
      .default(QueueName("q"))
      .withArguments("x-queue-type" -> ("quorum": SafeArg))

    val result = config.validatedArguments
    result.isRight shouldBe true
    result.toOption.get.keys should contain("x-queue-type")
  }

  behavior of "DeclarationExchangeConfig"

  it should "create default config with correct defaults" in {
    val config = DeclarationExchangeConfig.default(ExchangeName("test-exchange"), ExchangeType.Topic)
    config.exchangeName shouldBe ExchangeName("test-exchange")
    config.exchangeType shouldBe ExchangeType.Topic
    config.durable shouldBe NonDurable
    config.autoDelete shouldBe NonAutoDelete
    config.internal shouldBe NonInternal
    config.arguments shouldBe empty
  }

  it should "create config with different exchange types" in {
    DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Direct).exchangeType shouldBe ExchangeType.Direct
    DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.FanOut).exchangeType shouldBe ExchangeType.FanOut
    DeclarationExchangeConfig
      .default(ExchangeName("ex"), ExchangeType.Headers)
      .exchangeType shouldBe ExchangeType.Headers
  }

  it should "set durable with withDurable" in {
    val config = DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Topic).withDurable
    config.durable shouldBe Durable
  }

  it should "set non-durable with withNonDurable" in {
    val config = DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Topic).withDurable.withNonDurable
    config.durable shouldBe NonDurable
  }

  it should "set auto-delete with withAutoDelete" in {
    val config = DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Topic).withAutoDelete
    config.autoDelete shouldBe AutoDelete
  }

  it should "set non-auto-delete with withNonAutoDelete" in {
    val config =
      DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Topic).withAutoDelete.withNonAutoDelete
    config.autoDelete shouldBe NonAutoDelete
  }

  it should "set internal with withInternal" in {
    val config = DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Topic).withInternal
    config.internal shouldBe Internal
  }

  it should "set non-internal with withNonInternal" in {
    val config = DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Topic).withInternal.withNonInternal
    config.internal shouldBe NonInternal
  }

  it should "set arguments with withArguments (Map)" in {
    val args: Arguments = Map("alternate-exchange" -> ("alt-ex": SafeArg))
    val config          = DeclarationExchangeConfig.default(ExchangeName("ex"), ExchangeType.Topic).withArguments(args)
    config.arguments shouldBe args
  }

  it should "set arguments with withArguments (varargs)" in {
    val config = DeclarationExchangeConfig
      .default(ExchangeName("ex"), ExchangeType.Topic)
      .withArguments("alternate-exchange" -> ("alt-ex": SafeArg))
    config.arguments.keys should contain("alternate-exchange")
  }

  it should "chain multiple builder methods" in {
    val config = DeclarationExchangeConfig
      .default(ExchangeName("ex"), ExchangeType.Direct)
      .withDurable
      .withAutoDelete
      .withInternal
      .withArguments("alternate-exchange" -> ("backup": SafeArg))

    config.durable shouldBe Durable
    config.autoDelete shouldBe AutoDelete
    config.internal shouldBe Internal
    config.arguments.keys should contain("alternate-exchange")
  }
}
