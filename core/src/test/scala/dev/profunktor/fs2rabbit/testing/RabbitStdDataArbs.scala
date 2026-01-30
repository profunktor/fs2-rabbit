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

package dev.profunktor.fs2rabbit.testing

import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{DecimalVal, TimestampVal}
import org.scalacheck.*

import java.time.Instant
import java.util.Date

object RabbitStdDataArbs {

  implicit def bigDecimalArb(implicit a: Arbitrary[DecimalVal]): Arbitrary[BigDecimal] =
    Arbitrary(a.arbitrary.map(_.sizeLimitedBigDecimal))

  implicit def bigIntArb(implicit a: Arbitrary[DecimalVal]): Arbitrary[BigInt] =
    Arbitrary(bigDecimalArb.arbitrary.map(_.toBigInt))

  implicit def arbInstant(implicit a: Arbitrary[TimestampVal]): Arbitrary[Instant] =
    Arbitrary(a.arbitrary.map(_.instantWithOneSecondAccuracy))

  implicit def arbDate(implicit a: Arbitrary[TimestampVal]): Arbitrary[Date] =
    Arbitrary(a.arbitrary.map(_.toValueWriterCompatibleJava))
}
