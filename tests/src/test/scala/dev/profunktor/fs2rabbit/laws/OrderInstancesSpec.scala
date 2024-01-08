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

package dev.profunktor.fs2rabbit.laws

import java.time.Instant

import cats._
import cats.implicits._
import cats.kernel.laws.discipline._
import dev.profunktor.fs2rabbit.model.AmqpFieldValue._
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.testkit._
import org.scalacheck.Arbitrary._
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.FunSpecDiscipline

class OrderInstancesSpec extends AnyFunSpec with FunSpecDiscipline with ScalaCheckPropertyChecks {
  implicit val orderInstant: Order[Instant] = Order.by(_.getEpochSecond)

  checkAll("ExchangeName.OrderLaws", OrderTests[ExchangeName].order)
  checkAll("QueueName.OrderLaws", OrderTests[QueueName].order)
  checkAll("RoutingKey.OrderLaws", OrderTests[RoutingKey].order)
  checkAll("DeliveryTag.OrderLaws", OrderTests[DeliveryTag].order)
  checkAll("ConsumerTag.OrderLaws", OrderTests[ConsumerTag].order)
  checkAll("Instant.OrderLaws", OrderTests[Instant](instantOrderWithSecondPrecision).order)
  checkAll("DeliveryMode.OrderLaws", OrderTests[DeliveryMode].order)
  checkAll("ShortString.OrderLaws", OrderTests[ShortString].order)
  checkAll("TimestampVal.OrderLaws", OrderTests[TimestampVal].order)
  checkAll("DecimalVal.OrderLaws", OrderTests[DecimalVal].order)
  checkAll("ByteVal.OrderLaws", OrderTests[ByteVal].order)
  checkAll("DoubleVal.OrderLaws", OrderTests[DoubleVal].order)
  checkAll("FloatVal.OrderLaws", OrderTests[FloatVal].order)
  checkAll("ShortVal.OrderLaws", OrderTests[ShortVal].order)
  checkAll("BooleanVal.OrderLaws", OrderTests[BooleanVal].order)
  checkAll("IntVal.OrderLaws", OrderTests[IntVal].order)
  checkAll("LongVal.OrderLaws", OrderTests[LongVal].order)
  checkAll("StringVal.OrderLaws", OrderTests[StringVal].order)
  checkAll("NullVal.OrderLaws", OrderTests[NullVal.type].order)
}
