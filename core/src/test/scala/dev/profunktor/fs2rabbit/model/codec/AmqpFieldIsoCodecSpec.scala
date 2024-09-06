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

package dev.profunktor.fs2rabbit.model.codec

import cats.data.{NonEmptyList, NonEmptySeq}
import dev.profunktor.fs2rabbit.model.AmqpFieldValue
import dev.profunktor.fs2rabbit.model.AmqpFieldValue._
import dev.profunktor.fs2rabbit.model.codec.AmqpFieldDecoder.DecodingError
import dev.profunktor.fs2rabbit.testing._
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

import java.time.Instant
import java.util.Date
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class AmqpFieldIsoCodecSpec extends AnyFunSuite with PropertyChecks with Matchers {

  import AmqpPropertiesArbs._
  import CatsCollectionsArbs._
  import RabbitStdDataArbs._

  implicit val decodingErrorArb: Arbitrary[DecodingError] =
    Arbitrary(Arbitrary.arbitrary[String].map(DecodingError(_)))

  // miscelaneous
  testAmqpFieldCodecIso[Unit]
  testAmqpFieldCodecIso[StringVal]
  testAmqpFieldCodecIso[AmqpFieldValue]

  // time
  testAmqpFieldCodecIso[Instant]
  testAmqpFieldCodecIso[Date]

  // string
  testAmqpFieldCodecIso[String]

  // boolean
  testAmqpFieldCodecIso[Boolean]

  // numbers
  testAmqpFieldCodecIso[Int]
  testAmqpFieldCodecIso[Long]
  testAmqpFieldCodecIso[Byte]
  testAmqpFieldCodecIso[Short]
  testAmqpFieldCodecIso[Float]
  testAmqpFieldCodecIso[Double]
  testAmqpFieldCodecIso[BigInt]
  testAmqpFieldCodecIso[BigDecimal]

  // option
  testAmqpFieldCodecIso[Option[String]]

  // either
  testAmqpFieldCodecIso[Either[DecodingError, Int]]
  testAmqpFieldCodecIso[Either[String, Int]]

  // array
  testAmqpFieldArrayCodecIso[Byte]
  testAmqpFieldArrayCodecIso[Int]

  testAmqpFieldCodecIso[List[Int]]
  testAmqpFieldCodecIso[Set[Int]]

  // cats collections
  testAmqpFieldCodecIso[NonEmptyList[Int]]
  testAmqpFieldCodecIso[NonEmptySeq[Int]]

  // noinspection UnitMethodIsParameterless
  @inline
  private def testAmqpFieldCodecIso[A: AmqpFieldEncoder: AmqpFieldDecoder: Arbitrary: TypeTag]: Unit = {
    val tpeName = typeOf[A].toString
    test(s"Codes for [$tpeName] should be isomorphic") {
      forAll { (value: A) =>
        AmqpFieldDecoder[A].decode(AmqpFieldEncoder[A].encode(value)) shouldBe Right(value)
      }
    }
  }

  // noinspection UnitMethodIsParameterless
  @inline
  private def testAmqpFieldArrayCodecIso[T: AmqpFieldEncoder: AmqpFieldDecoder: Arbitrary: TypeTag: ClassTag]: Unit = {
    val tpeName = typeOf[T].toString
    test(s"Codes for Array[$tpeName] should be isomorphic") {
      forAll { (value: Array[T]) =>
        AmqpFieldDecoder[Array[T]].decode(AmqpFieldEncoder[Array[T]].encode(value)).toOption.get.sameElements(value)
      }
    }
  }
}
