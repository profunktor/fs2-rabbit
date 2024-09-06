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

package dev.profunktor.fs2rabbit

import org.scalacheck.Gen
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class SafeArgSpec extends AnyFunSuite with PropertyChecks {

  import arguments._

  def safeArg[A](value: A)(implicit ev: SafeArgument[A]): Assertion = {
    assert(ev.toJavaType(value) != null)
    assert(ev.toObject(value) != null)
  }

  def safeConversion(args: Arguments): Assertion = {
    val converted: java.util.Map[String, Object] = args
    assert(converted != null)
  }

  val tupleGen: Gen[(String, String)] =
    for {
      x <- Gen.alphaStr
      y <- Gen.alphaStr
    } yield (x, y)

  val stringGen: Gen[String]           = Gen.alphaStr
  val intGen: Gen[Int]                 = Gen.posNum[Int]
  val longGen: Gen[Long]               = Gen.posNum[Long]
  val doubleGen: Gen[Double]           = Gen.posNum[Double]
  val floatGen: Gen[Float]             = Gen.posNum[Float]
  val shortGen: Gen[Short]             = Gen.posNum[Short]
  val booleanGen: Gen[Boolean]         = Gen.oneOf(Seq(true, false))
  val byteGen: Gen[Byte]               = intGen.map(_.toByte)
  val dateGen: Gen[java.util.Date]     = Gen.calendar.map(_.getTime)
  val bigDecimalGen: Gen[BigDecimal]   = longGen.map(BigDecimal.apply)
  val listGen: Gen[List[String]]       = Gen.listOf(stringGen)
  val mapGen: Gen[Map[String, String]] = Gen.mapOf(tupleGen)

  forAll(stringGen)(safeArg(_))
  forAll(intGen)(safeArg(_))
  forAll(longGen)(safeArg(_))
  forAll(doubleGen)(safeArg(_))
  forAll(floatGen)(safeArg(_))
  forAll(shortGen)(safeArg(_))
  forAll(booleanGen)(safeArg(_))
  forAll(byteGen)(safeArg(_))
  forAll(dateGen)(safeArg(_))
  forAll(bigDecimalGen)(safeArg(_))
  forAll(listGen)(safeArg(_))
  forAll(mapGen)(safeArg(_))

  test("Arguments conversion") {
    safeConversion(Map("key" -> "value"))
    safeConversion(Map("key" -> true))
    safeConversion(Map("key" -> 123))
    safeConversion(Map("key" -> 456L))
    safeConversion(Map("key" -> 1.87))
    safeConversion(Map("key" -> new java.util.Date()))
    safeConversion(Map("key" -> "value".getBytes.toList))
    safeConversion(Map("key" -> Map("nested" -> "value")))
    safeConversion(Map("key" -> List(1, 2, 3)))
  }

}
