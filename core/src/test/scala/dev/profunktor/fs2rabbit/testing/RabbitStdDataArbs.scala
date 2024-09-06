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

package dev.profunktor.fs2rabbit.testing

import dev.profunktor.fs2rabbit.model.AmqpFieldValue.DecimalVal
import org.scalacheck._

import java.time.Instant
import java.util.Date

object RabbitStdDataArbs {

  implicit val bigDecimalArb: Arbitrary[BigDecimal] =
    Arbitrary(Arbitrary.arbBigDecimal.arbitrary.filter(DecimalVal.from(_).isDefined))

  implicit val bigIntArb: Arbitrary[BigInt] =
    Arbitrary(bigDecimalArb.arbitrary.map(_.toBigInt))

  implicit val arbInstant: Arbitrary[Instant] =
    Arbitrary(Arbitrary.arbInstant.arbitrary.map(_.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))

  implicit val arbDate: Arbitrary[Date] =
    Arbitrary(
      Arbitrary.arbDate.arbitrary
        .map(_.toInstant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
        .map(Date.from)
    )

}
