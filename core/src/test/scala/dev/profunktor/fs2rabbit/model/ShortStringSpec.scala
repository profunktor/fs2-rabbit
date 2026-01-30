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
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ShortStringSpec extends AnyFunSuite with Matchers {

  test("MaxByteLength should be 255") {
    ShortString.MaxByteLength shouldBe 255
  }

  test("isValid should check UTF-8 byte length correctly") {
    ShortString.isValid("") shouldBe true
    ShortString.isValid("hello") shouldBe true
    ShortString.isValid("a" * 255) shouldBe true
    ShortString.isValid("a" * 256) shouldBe false
    ShortString.isValid("a" * 1000) shouldBe false
  }

  test("isValid should handle multi-byte UTF-8 characters correctly") {
    val twoByteChar   = "\u00e9"
    val threeByteChar = "\u4e2d"

    twoByteChar.getBytes("utf-8").length shouldBe 2
    threeByteChar.getBytes("utf-8").length shouldBe 3
    ShortString.isValid(twoByteChar * 127) shouldBe true
    ShortString.isValid(twoByteChar * 128) shouldBe false
    ShortString.isValid(threeByteChar * 85) shouldBe true
    ShortString.isValid(threeByteChar * 86) shouldBe false
    val base       = "a" * 253
    val twoByteEnd = base + twoByteChar

    ShortString.isValid(twoByteEnd) shouldBe true
    ShortString.isValid(base + "aa" + twoByteChar) shouldBe false
  }

  test("from should return Some for valid strings and None for invalid") {
    ShortString.from("hello") shouldBe a[Some[_]]
    ShortString.from("") shouldBe a[Some[_]]
    ShortString.from("a" * 255) shouldBe a[Some[_]]
    ShortString.from("a" * 256) shouldBe None
  }

  test("unsafeFrom should create ShortString without validation and str should return the value") {
    val longString = "a" * 300
    val ss         = ShortString.unsafeFrom(longString)

    ss.str shouldBe longString
    ShortString.unsafeFrom("test").str shouldBe "test"
  }

  test("Order and Ordering instances should compare by underlying string") {
    val order    = Order[ShortString]
    val ordering = Ordering[ShortString]
    val a        = ShortString.unsafeFrom("a")
    val b        = ShortString.unsafeFrom("b")

    order.compare(a, b) should be < 0
    order.compare(b, a) should be > 0
    order.compare(a, ShortString.unsafeFrom("a")) shouldBe 0
    ordering.compare(a, b) should be < 0
    ordering.compare(b, a) should be > 0
  }
}
