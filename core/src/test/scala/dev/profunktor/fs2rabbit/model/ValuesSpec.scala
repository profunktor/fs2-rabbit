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

package dev.profunktor.fs2rabbit.model

import cats.Order
import cats.kernel.CommutativeSemigroup
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ValuesSpec extends AnyFunSuite with Matchers {

  test("QueueType.asString should return correct string representation") {
    QueueType.Classic.asString shouldBe "classic"
    QueueType.Quorum.asString shouldBe "quorum"
    QueueType.Stream.asString shouldBe "stream"
  }

  test("ExchangeType.asString should return correct string representation") {
    ExchangeType.Direct.asString shouldBe "direct"
    ExchangeType.FanOut.asString shouldBe "fanout"
    ExchangeType.Headers.asString shouldBe "headers"
    ExchangeType.Topic.asString shouldBe "topic"
    ExchangeType.`X-Delayed-Message`.asString shouldBe "x-delayed-message"
  }

  test("DeliveryMode.value should return correct int values") {
    DeliveryMode.NonPersistent.value shouldBe 1
    DeliveryMode.Persistent.value shouldBe 2
  }

  test("DeliveryMode.fromInt should return correct Option values") {
    DeliveryMode.fromInt(1) shouldBe Some(DeliveryMode.NonPersistent)
    DeliveryMode.fromInt(2) shouldBe Some(DeliveryMode.Persistent)
    DeliveryMode.fromInt(0) shouldBe None
    DeliveryMode.fromInt(3) shouldBe None
    DeliveryMode.fromInt(-1) shouldBe None
  }

  test("DeliveryMode.unsafeFromInt should return value or throw") {
    DeliveryMode.unsafeFromInt(1) shouldBe DeliveryMode.NonPersistent
    DeliveryMode.unsafeFromInt(2) shouldBe DeliveryMode.Persistent
    assertThrows[IllegalArgumentException](DeliveryMode.unsafeFromInt(0))
    assertThrows[IllegalArgumentException](DeliveryMode.unsafeFromInt(3))
  }

  test("Order instances should compare value types correctly") {
    def assertOrdering[T: Order](lesser: T, greater: T, equal: T): Unit = {
      val order = Order[T]

      order.compare(lesser, greater) should be < 0
      order.compare(greater, lesser) should be > 0
      order.compare(equal, equal) shouldBe 0
    }

    assertOrdering(ExchangeName("a"), ExchangeName("b"), ExchangeName("a"))
    assertOrdering(QueueName("a"), QueueName("b"), QueueName("a"))
    assertOrdering(RoutingKey("a"), RoutingKey("b"), RoutingKey("a"))
    assertOrdering(DeliveryTag(1L), DeliveryTag(2L), DeliveryTag(1L))
    assertOrdering(ConsumerTag("a"), ConsumerTag("b"), ConsumerTag("a"))

    val dmOrder = Order[DeliveryMode]

    dmOrder.compare(DeliveryMode.NonPersistent, DeliveryMode.Persistent) should be < 0
    dmOrder.compare(DeliveryMode.Persistent, DeliveryMode.NonPersistent) should be > 0
    dmOrder.compare(DeliveryMode.NonPersistent, DeliveryMode.NonPersistent) shouldBe 0
  }

  test("CommutativeSemigroup[DeliveryTag] should return max of two tags") {
    val semigroup = CommutativeSemigroup[DeliveryTag]

    semigroup.combine(DeliveryTag(1L), DeliveryTag(5L)) shouldBe DeliveryTag(5L)
    semigroup.combine(DeliveryTag(5L), DeliveryTag(1L)) shouldBe DeliveryTag(5L)
    semigroup.combine(DeliveryTag(3L), DeliveryTag(3L)) shouldBe DeliveryTag(3L)
  }
}
