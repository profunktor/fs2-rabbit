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

package dev.profunktor.fs2rabbit

import java.io.{DataInputStream, DataOutputStream, InputStream, OutputStream}
import java.time.Instant
import com.rabbitmq.client.impl.{ValueReader, ValueWriter}
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.*
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, ShortString}
import dev.profunktor.fs2rabbit.testing.AmqpPropertiesArbs
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks as PropertyChecks

class AmqpFieldValueSpec extends AnyFlatSpecLike with PropertyChecks with Matchers {

  import AmqpPropertiesArbs.*

  it should "convert from and to Java primitive header values" in {
    val intVal    = IntVal(1)
    val longVal   = LongVal(2L)
    val stringVal = StringVal("hey")
    val arrayVal  = ArrayVal(Vector(IntVal(3), IntVal(2), IntVal(1)))

    AmqpFieldValue.unsafeFrom(intVal.toValueWriterCompatibleJava) should be(intVal)
    AmqpFieldValue.unsafeFrom(longVal.toValueWriterCompatibleJava) should be(longVal)
    AmqpFieldValue.unsafeFrom(stringVal.toValueWriterCompatibleJava) should be(stringVal)
    AmqpFieldValue.unsafeFrom("fs2") should be(StringVal("fs2"))
    AmqpFieldValue.unsafeFrom(arrayVal.toValueWriterCompatibleJava) should be(arrayVal)
  }
  it should "preserve the same value after a round-trip through impure and from" in
    forAll { (amqpHeaderVal: AmqpFieldValue) =>
      AmqpFieldValue.unsafeFrom(amqpHeaderVal.toValueWriterCompatibleJava) == amqpHeaderVal
    }

  it should "preserve the same values after a round-trip through the Java ValueReader and ValueWriter" in
    forAll(assertThatValueIsPreservedThroughJavaWriteAndRead _)

  it should "preserve a specific StringVal that previously failed after a round-trip through the Java ValueReader and ValueWriter" in
    assertThatValueIsPreservedThroughJavaWriteAndRead(StringVal("kyvmqzlbjivLqQFukljghxdowkcmjklgSeybdy"))

  it should "preserve a specific DateVal created from an Instant that has millisecond accuracy after a round-trip through the Java ValueReader and ValueWriter" in {
    val instant   = Instant.parse("4000-11-03T20:17:29.57Z")
    val myDateVal = TimestampVal.from(instant)

    assertThatValueIsPreservedThroughJavaWriteAndRead(myDateVal)
  }

  "DecimalVal" should "reject a BigDecimal of an unscaled value with 33 bits..." in {
    DecimalVal.from(BigDecimal(Int.MaxValue) + BigDecimal(1)) should be(None)
  }
  it should "reject a BigDecimal with a scale over octet size" in {
    DecimalVal.from(new java.math.BigDecimal(java.math.BigInteger.valueOf(12345L), 1000)) should be(None)
  }

  // We need to wrap things in a dummy table because the method that would be
  // great to test with ValueReader, readFieldValue, is private, and so we
  // have to call the next best thing, readTable.
  private def wrapInDummyTable(value: AmqpFieldValue): TableVal =
    TableVal(Map(ShortString.unsafeFrom("dummyKey") -> value))

  private def createWriterFromQueue(outputResults: collection.mutable.Queue[Byte]): ValueWriter =
    new ValueWriter(
      new DataOutputStream((b: Int) => outputResults.enqueue(b.toByte))
    )

  private def createReaderFromQueue(input: collection.mutable.Queue[Byte]): ValueReader = {
    val inputStream = new InputStream {
      override def read(): Int =
        try {
          val result = input.dequeue()
          // A signed -> unsigned conversion because bytes by default are
          // converted into signed ints, which is bad when the API of read
          // states that negative numbers indicate EOF...
          0xff & result.toInt
        } catch {
          case _: NoSuchElementException => -1
        }

      override def available(): Int = {
        val result = input.size
        result
      }
    }
    new ValueReader(new DataInputStream(inputStream))
  }

  private def assertThatValueIsPreservedThroughJavaWriteAndRead(amqpHeaderVal: AmqpFieldValue): Assertion = {
    val outputResultsAsTable = collection.mutable.Queue.empty[Byte]
    val tableWriter          = createWriterFromQueue(outputResultsAsTable)
    tableWriter.writeTable(wrapInDummyTable(amqpHeaderVal).toValueWriterCompatibleJava)

    val reader    = createReaderFromQueue(outputResultsAsTable)
    val readValue = reader.readTable()
    AmqpFieldValue.unsafeFrom(readValue) should be(wrapInDummyTable(amqpHeaderVal))
  }

  "toValueWriterCompatibleJava" should "return correct Java boxed types for all AmqpFieldValue subtypes" in {
    import scodec.bits.ByteVector

    ByteVal(42.toByte).toValueWriterCompatibleJava shouldBe java.lang.Byte.valueOf(42.toByte)
    ShortVal(1000.toShort).toValueWriterCompatibleJava shouldBe java.lang.Short.valueOf(1000.toShort)
    IntVal(42).toValueWriterCompatibleJava shouldBe java.lang.Integer.valueOf(42)
    LongVal(42L).toValueWriterCompatibleJava shouldBe java.lang.Long.valueOf(42L)
    FloatVal(3.14f).toValueWriterCompatibleJava shouldBe java.lang.Float.valueOf(3.14f)
    DoubleVal(3.14159).toValueWriterCompatibleJava shouldBe java.lang.Double.valueOf(3.14159)
    BooleanVal(true).toValueWriterCompatibleJava shouldBe java.lang.Boolean.TRUE
    BooleanVal(false).toValueWriterCompatibleJava shouldBe java.lang.Boolean.FALSE
    assert(NullVal.toValueWriterCompatibleJava == null)

    val bytes = Array[Byte](1, 2, 3)
    ByteArrayVal(ByteVector(bytes)).toValueWriterCompatibleJava.sameElements(bytes) shouldBe true
  }

  "AmqpFieldValue.unsafeFrom" should "convert null to NullVal" in {
    AmqpFieldValue.unsafeFrom(null) shouldBe NullVal
  }

  "Eq[AmqpFieldValue]" should "compare values correctly" in {
    import cats.Eq
    val eq = Eq[AmqpFieldValue]

    eq.eqv(IntVal(42), IntVal(42)) shouldBe true
    eq.eqv(StringVal("hello"), StringVal("hello")) shouldBe true
    eq.eqv(NullVal, NullVal) shouldBe true
    eq.eqv(IntVal(42), IntVal(43)) shouldBe false
    eq.eqv(IntVal(42), StringVal("42")) shouldBe false
    eq.eqv(IntVal(42), LongVal(42L)) shouldBe false
    eq.eqv(FloatVal(1.0f), DoubleVal(1.0)) shouldBe false
  }
}
