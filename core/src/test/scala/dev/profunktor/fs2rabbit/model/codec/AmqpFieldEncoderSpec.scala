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

package dev.profunktor.fs2rabbit.model.codec

import cats.Contravariant
import cats.data.{NonEmptyList, NonEmptySeq, NonEmptySet}
import dev.profunktor.fs2rabbit.model.AmqpFieldValue
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.*
import dev.profunktor.fs2rabbit.model.ShortString
import dev.profunktor.fs2rabbit.model.codec.AmqpFieldDecoder.DecodingError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scodec.bits.ByteVector

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class AmqpFieldEncoderSpec extends AnyFunSuite with Matchers {

  test("null/unit encoders should encode to NullVal") {
    AmqpFieldEncoder[Unit].encode(()) shouldBe NullVal
    AmqpFieldEncoder[Null].encode(null) shouldBe NullVal
  }

  test("primitive encoders should encode to corresponding AmqpFieldValue types") {
    AmqpFieldEncoder[String].encode("hello") shouldBe StringVal("hello")
    AmqpFieldEncoder[String].encode("") shouldBe StringVal("")
    AmqpFieldEncoder[Boolean].encode(true) shouldBe BooleanVal(true)
    AmqpFieldEncoder[Boolean].encode(false) shouldBe BooleanVal(false)
  }

  test("numeric encoders should encode to corresponding AmqpFieldValue types") {
    AmqpFieldEncoder[Byte].encode(42.toByte) shouldBe ByteVal(42.toByte)
    AmqpFieldEncoder[Short].encode(42.toShort) shouldBe ShortVal(42.toShort)
    AmqpFieldEncoder[Int].encode(42) shouldBe IntVal(42)
    AmqpFieldEncoder[Long].encode(42L) shouldBe LongVal(42L)
    AmqpFieldEncoder[Float].encode(42.5f) shouldBe FloatVal(42.5f)
    AmqpFieldEncoder[Double].encode(42.5) shouldBe DoubleVal(42.5)
  }

  test("numeric encoders should handle boundary values") {
    AmqpFieldEncoder[Byte].encode(Byte.MinValue) shouldBe ByteVal(Byte.MinValue)
    AmqpFieldEncoder[Byte].encode(Byte.MaxValue) shouldBe ByteVal(Byte.MaxValue)
    AmqpFieldEncoder[Int].encode(Int.MinValue) shouldBe IntVal(Int.MinValue)
    AmqpFieldEncoder[Int].encode(Int.MaxValue) shouldBe IntVal(Int.MaxValue)
    AmqpFieldEncoder[Long].encode(Long.MinValue) shouldBe LongVal(Long.MinValue)
    AmqpFieldEncoder[Long].encode(Long.MaxValue) shouldBe LongVal(Long.MaxValue)
  }

  test("time encoders should encode to TimestampVal") {
    val now  = Instant.now.truncatedTo(ChronoUnit.SECONDS)
    val date = Date.from(now)

    AmqpFieldEncoder[Instant].encode(now) shouldBe TimestampVal.from(now)
    AmqpFieldEncoder[Date].encode(date) shouldBe TimestampVal.from(date)
  }

  test("bigDecimal/bigInt encoders should encode valid values to DecimalVal") {
    val bd = BigDecimal(123.45)
    val bi = BigInt(42)

    AmqpFieldEncoder[BigDecimal].encode(bd) shouldBe DecimalVal.unsafeFrom(bd)
    AmqpFieldEncoder[BigInt].encode(bi) shouldBe DecimalVal.unsafeFrom(BigDecimal(42))
  }

  test("bigDecimal/bigInt encoders should encode invalid values to NullVal") {
    val invalidBd = BigDecimal(Int.MaxValue) + BigDecimal(1)
    val invalidBi = BigInt(Int.MaxValue) + BigInt(1)

    AmqpFieldEncoder[BigDecimal].encode(invalidBd) shouldBe NullVal
    AmqpFieldEncoder[BigInt].encode(invalidBi) shouldBe NullVal
  }

  test("decodingErrorEncoder should encode to StringVal with message") {
    val error = DecodingError("test error")

    AmqpFieldEncoder[DecodingError].encode(error) shouldBe StringVal("test error")
  }

  test("optionEncoder should encode Some to value and None to NullVal") {
    AmqpFieldEncoder[Option[Int]].encode(Some(42)) shouldBe IntVal(42)
    AmqpFieldEncoder[Option[String]].encode(Some("hello")) shouldBe StringVal("hello")
    AmqpFieldEncoder[Option[Int]].encode(None) shouldBe NullVal
    AmqpFieldEncoder[Option[String]].encode(None) shouldBe NullVal
  }

  test("eitherEncoder should encode Left and Right to their encoded values") {
    AmqpFieldEncoder[Either[String, Int]].encode(Left("error")) shouldBe StringVal("error")
    AmqpFieldEncoder[Either[String, Int]].encode(Right(42)) shouldBe IntVal(42)
  }

  test("byte array encoders should encode to ByteArrayVal") {
    val bv  = ByteVector(1, 2, 3)
    val arr = Array[Byte](1, 2, 3)

    AmqpFieldEncoder[ByteVector].encode(bv) shouldBe ByteArrayVal(bv)
    AmqpFieldEncoder[Array[Byte]].encode(arr) shouldBe ByteArrayVal(ByteVector(arr))
  }

  test("collection encoders should encode to ArrayVal") {
    AmqpFieldEncoder[Array[Int]].encode(Array(1, 2, 3)) shouldBe ArrayVal(Vector(IntVal(1), IntVal(2), IntVal(3)))
    AmqpFieldEncoder[List[Int]].encode(List(1, 2, 3)) shouldBe ArrayVal(Vector(IntVal(1), IntVal(2), IntVal(3)))
    AmqpFieldEncoder[Seq[String]].encode(Seq("a", "b")) shouldBe ArrayVal(Vector(StringVal("a"), StringVal("b")))

    val setResult           = AmqpFieldEncoder[Set[Int]].encode(Set(1, 2))
    val ArrayVal(setValues) = setResult: @unchecked

    setValues.toSet shouldBe Set(IntVal(1), IntVal(2))
  }

  test("cats collection encoders should encode to ArrayVal") {
    AmqpFieldEncoder[NonEmptyList[Int]].encode(NonEmptyList.of(1, 2, 3)) shouldBe ArrayVal(
      Vector(IntVal(1), IntVal(2), IntVal(3))
    )
    AmqpFieldEncoder[NonEmptySeq[Int]].encode(NonEmptySeq.of(1, 2, 3)) shouldBe ArrayVal(
      Vector(IntVal(1), IntVal(2), IntVal(3))
    )
    import cats.Order
    implicit val intOrder: Order[Int] = Order.fromOrdering

    val nesResult           = AmqpFieldEncoder[NonEmptySet[Int]].encode(NonEmptySet.of(3, 1, 2))
    val ArrayVal(nesValues) = nesResult: @unchecked

    nesValues.toSet shouldBe Set(IntVal(1), IntVal(2), IntVal(3))
  }

  test("mapEncoder should encode Map[ShortString, AmqpFieldValue] to TableVal") {
    val key = ShortString.unsafeFrom("key")
    val map = Map(key -> IntVal(42))

    AmqpFieldEncoder[Map[ShortString, AmqpFieldValue]].encode(map) shouldBe TableVal(map)
  }

  test("amqpFieldValueEncoder should encode as identity") {
    val intVal    = IntVal(42)
    val stringVal = StringVal("hello")

    AmqpFieldEncoder[IntVal].encode(intVal) shouldBe intVal
    AmqpFieldEncoder[StringVal].encode(stringVal) shouldBe stringVal
    AmqpFieldEncoder[AmqpFieldValue].encode(intVal) shouldBe intVal
  }

  test("contramap and Contravariant instance should transform input before encoding") {
    case class Age(value: Int)
    case class Wrapper(value: String)

    val ageEncoder     = AmqpFieldEncoder[Int].contramap[Age](_.value)
    val contravariant  = Contravariant[AmqpFieldEncoder]
    val wrapperEncoder = contravariant.contramap(AmqpFieldEncoder[String])((w: Wrapper) => w.value)

    ageEncoder.encode(Age(25)) shouldBe IntVal(25)
    wrapperEncoder.encode(Wrapper("test")) shouldBe StringVal("test")
  }

  test("instance should create encoder from function") {
    val customEncoder = AmqpFieldEncoder.instance[Int](i => StringVal(i.toString))

    customEncoder.encode(42) shouldBe StringVal("42")
  }
}
