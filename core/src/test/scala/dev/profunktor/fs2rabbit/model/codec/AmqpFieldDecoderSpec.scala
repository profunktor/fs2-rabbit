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

import cats.data.{NonEmptyList, NonEmptySeq}
import dev.profunktor.fs2rabbit.model.AmqpFieldValue
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.*
import dev.profunktor.fs2rabbit.model.codec.AmqpFieldDecoder.DecodingError
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scodec.bits.ByteVector

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import scala.reflect.ClassTag

class AmqpFieldDecoderSpec extends AnyFunSuite with Matchers {

  // miscelaneous
  testAmqpFieldDecoder[Unit](NullVal, Right(()))
  testAmqpFieldDecoder[Any](StringVal("hello"), Right(StringVal("hello")))
  testAmqpFieldDecoder[StringVal](StringVal("hello"), Right(StringVal("hello")))
  testAmqpFieldDecoder[AmqpFieldValue](StringVal("hello"), Right(StringVal("hello")))

  // time
  val nowInstant: Instant = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  testAmqpFieldDecoder[Instant](TimestampVal.from(nowInstant), Right(nowInstant))
  testAmqpFieldDecoder[Date](TimestampVal.from(nowInstant), Right(Date.from(nowInstant)))

  // string
  testAmqpFieldDecoder[String](StringVal("hello"), Right("hello"))
  testAmqpFieldDecoder[String](StringVal(""), Right(""))

  // boolean
  testAmqpFieldDecoder[Boolean](BooleanVal(true), Right(true))
  testAmqpFieldDecoder[Boolean](BooleanVal(false), Right(false))

  // numbers
  testAmqpFieldDecoder[Int](IntVal(42), Right(42))
  testAmqpFieldDecoder[Long](LongVal(42L), Right(42L))
  testAmqpFieldDecoder[Byte](ByteVal(42.toByte), Right(42.toByte))
  testAmqpFieldDecoder[Short](ShortVal(42.toShort), Right(42.toShort))
  testAmqpFieldDecoder[Float](FloatVal(42.0f), Right(42.0f))
  testAmqpFieldDecoder[Double](DoubleVal(42.0), Right(42.0))
  testAmqpFieldDecoder[BigInt](DecimalVal.from(BigDecimal(42)).get, Right(BigInt(42)))
  testAmqpFieldDecoder[BigDecimal](DecimalVal.from(BigDecimal(42)).get, Right(BigDecimal(42)))

  // option
  testAmqpFieldDecoder[Option[String]](StringVal("hello"), Right(Some("hello")))
  testAmqpFieldDecoder[Option[String]](BooleanVal(false), Right(None))

  // either
  testAmqpFieldDecoder[Either[DecodingError, Int]](IntVal(42), Right(Right(42)))

  // array
  testAmqpFieldDecoderArray[Byte](ByteArrayVal(ByteVector("hello".getBytes)), Right("hello".getBytes))
  testAmqpFieldDecoderArray[Byte](ArrayVal(List(ByteVal(1.toByte)).toVector), Right(Array(1.toByte)))
  testAmqpFieldDecoderArray[Int](ArrayVal(List(IntVal(1), IntVal(2)).toVector), Right(Array(1, 2)))

  testAmqpFieldDecoder[List[Int]](ArrayVal(List(IntVal(1), IntVal(2)).toVector), Right(List(1, 2)))
  testAmqpFieldDecoder[Set[Int]](ArrayVal(List(IntVal(1), IntVal(2)).toVector), Right(Set(1, 2)))

  // cats collections
  testAmqpFieldDecoder[NonEmptyList[Int]](
    ArrayVal(List(IntVal(1), IntVal(2)).toVector),
    Right(NonEmptyList.of[Int](1, 2))
  )

  testAmqpFieldDecoder[NonEmptySeq[Int]](
    ArrayVal(List(IntVal(1), IntVal(2)).toVector),
    Right(NonEmptySeq.of[Int](1, 2))
  )

  @inline
  private def testAmqpFieldDecoder[T: AmqpFieldDecoder: ClassTag](
      value: AmqpFieldValue,
      expected: Either[DecodingError, T]
  ): Unit = {
    val tpeName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.capitalize
    test(s"AmqpFieldDecoder[$tpeName]($value) shouldBe $expected") {
      AmqpFieldDecoder[T].decode(value) shouldBe expected
    }
  }

  @inline
  private def testAmqpFieldDecoderArray[T: AmqpFieldDecoder: ClassTag](
      value: AmqpFieldValue,
      expected: Either[DecodingError, Array[T]]
  ): Unit = {
    val tpeName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.capitalize
    test(s"AmqpFieldDecoder[Array[$tpeName]]($value) shouldBe $expected") {
      (AmqpFieldDecoder[Array[T]].decode(value), expected) match {
        case (Left(err1), Left(err2)) => err1 shouldBe err2
        case (Right(v1), Right(v2))   => v1.sameElements(v2) shouldBe true
        case (Right(value), _)        => fail(s"Expected $expected but got [${value.mkString("Array(", ", ", ")")}]")
        case (Left(err), _)           => fail(s"Expected $expected but got $err")
      }
    }
  }

  test("decoders should fail with descriptive error for wrong types") {
    def assertDecodingError[T: AmqpFieldDecoder](input: AmqpFieldValue, expectedType: String): Unit = {
      val result = AmqpFieldDecoder[T].decode(input)

      result shouldBe a[Left[_, _]]
      result.left.toOption.get.getMessage should include(s"Expected $expectedType")
    }

    assertDecodingError[String](IntVal(42), "StringVal")
    assertDecodingError[Int](StringVal("not a number"), "IntVal")
    assertDecodingError[Boolean](StringVal("true"), "BooleanVal")
    assertDecodingError[Instant](StringVal("2024-01-01"), "TimestampVal")
    assertDecodingError[Unit](IntVal(0), "NullVal")
  }

  test("intDecoder should widen from smaller numeric types") {
    AmqpFieldDecoder[Int].decode(ByteVal(42.toByte)) shouldBe Right(42)
    AmqpFieldDecoder[Int].decode(ShortVal(1000.toShort)) shouldBe Right(1000)
  }

  test("longDecoder should widen from smaller numeric types") {
    AmqpFieldDecoder[Long].decode(ByteVal(42.toByte)) shouldBe Right(42L)
    AmqpFieldDecoder[Long].decode(ShortVal(1000.toShort)) shouldBe Right(1000L)
    AmqpFieldDecoder[Long].decode(IntVal(100000)) shouldBe Right(100000L)
  }

  test("floatDecoder should widen from smaller numeric types") {
    AmqpFieldDecoder[Float].decode(ByteVal(42.toByte)) shouldBe Right(42.0f)
    AmqpFieldDecoder[Float].decode(ShortVal(1000.toShort)) shouldBe Right(1000.0f)
    AmqpFieldDecoder[Float].decode(IntVal(100)) shouldBe Right(100.0f)
    AmqpFieldDecoder[Float].decode(LongVal(100L)) shouldBe Right(100.0f)
  }

  test("doubleDecoder should widen from smaller numeric types") {
    AmqpFieldDecoder[Double].decode(ByteVal(42.toByte)) shouldBe Right(42.0)
    AmqpFieldDecoder[Double].decode(ShortVal(1000.toShort)) shouldBe Right(1000.0)
    AmqpFieldDecoder[Double].decode(IntVal(100)) shouldBe Right(100.0)
    AmqpFieldDecoder[Double].decode(LongVal(100L)) shouldBe Right(100.0)
    AmqpFieldDecoder[Double].decode(FloatVal(42.5f)) shouldBe Right(42.5)
  }

  test("bigDecimalDecoder should widen from all numeric types") {
    AmqpFieldDecoder[BigDecimal].decode(ByteVal(42.toByte)) shouldBe Right(BigDecimal(42))
    AmqpFieldDecoder[BigDecimal].decode(ShortVal(1000.toShort)) shouldBe Right(BigDecimal(1000))
    AmqpFieldDecoder[BigDecimal].decode(IntVal(100000)) shouldBe Right(BigDecimal(100000))
    AmqpFieldDecoder[BigDecimal].decode(LongVal(100000L)) shouldBe Right(BigDecimal(100000L))
    AmqpFieldDecoder[BigDecimal].decode(FloatVal(42.5f)) shouldBe Right(BigDecimal(42.5))
    AmqpFieldDecoder[BigDecimal].decode(DoubleVal(42.5)) shouldBe Right(BigDecimal(42.5))
  }

  test("combinators should transform and handle errors") {
    val mapDecoder     = AmqpFieldDecoder[Int].map(_ * 2)
    val emapDecoder    = AmqpFieldDecoder[Int].emap { n =>
      if (n > 0) Right(n.toString) else Left(DecodingError("must be positive"))
    }
    val optionDecoder  = AmqpFieldDecoder[Int].option
    val attemptDecoder = AmqpFieldDecoder[Int].attempt

    mapDecoder.decode(IntVal(21)) shouldBe Right(42)
    emapDecoder.decode(IntVal(42)) shouldBe Right("42")
    emapDecoder.decode(IntVal(-1)) shouldBe a[Left[_, _]]
    optionDecoder.decode(IntVal(42)) shouldBe Right(Some(42))
    optionDecoder.decode(StringVal("not int")) shouldBe Right(None)
    attemptDecoder.decode(IntVal(42)) shouldBe Right(Right(42))
    attemptDecoder.decode(StringVal("not int")).toOption.get shouldBe a[Left[_, _]]
  }

  test("non-empty collection decoders should fail for empty arrays") {
    val nelResult = AmqpFieldDecoder[NonEmptyList[Int]].decode(ArrayVal(Vector.empty))
    val nesResult = AmqpFieldDecoder[NonEmptySeq[Int]].decode(ArrayVal(Vector.empty))

    nelResult shouldBe a[Left[_, _]]
    nelResult.left.toOption.get.getMessage should include("empty list")
    nesResult shouldBe a[Left[_, _]]
    nesResult.left.toOption.get.getMessage should include("empty seq")
  }

  test("DecodingError.expectedButGot should format message correctly") {
    val error = DecodingError.expectedButGot("IntVal", "StringVal(hello)")

    error.getMessage shouldBe "Expected IntVal, but got StringVal(hello)"
  }
}
