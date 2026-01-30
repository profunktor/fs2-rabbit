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
}
