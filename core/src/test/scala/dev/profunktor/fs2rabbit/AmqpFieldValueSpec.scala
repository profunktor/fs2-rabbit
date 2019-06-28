/*
 * Copyright 2017-2019 ProfunKtor
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
import dev.profunktor.fs2rabbit.model.AmqpFieldValue._
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, ShortString}
import org.scalatest.{Assertion, FlatSpecLike, Matchers}

class AmqpFieldValueSpec extends FlatSpecLike with Matchers with AmqpPropertiesArbitraries {

  it should "convert from and to Java primitive header values" in {
    val intVal    = IntVal(1)
    val longVal   = LongVal(2L)
    val stringVal = StringVal("hey")
    val arrayVal  = ArrayVal(Vector(IntVal(3), IntVal(2), IntVal(1)))

    AmqpFieldValue.unsafeFromValueReaderOutput(intVal.toValueWriterCompatibleJava) should be(intVal)
    AmqpFieldValue.unsafeFromValueReaderOutput(longVal.toValueWriterCompatibleJava) should be(longVal)
    AmqpFieldValue.unsafeFromValueReaderOutput(stringVal.toValueWriterCompatibleJava) should be(stringVal)
    AmqpFieldValue.unsafeFromValueReaderOutput("fs2") should be(StringVal("fs2"))
    AmqpFieldValue.unsafeFromValueReaderOutput(arrayVal.toValueWriterCompatibleJava) should be(arrayVal)
  }
  it should "preserve the same value after a round-trip through impure and from" in {
    forAll { amqpHeaderVal: AmqpFieldValue =>
      AmqpFieldValue.unsafeFromValueReaderOutput(amqpHeaderVal.toValueWriterCompatibleJava) == amqpHeaderVal
    }
  }

  it should "preserve the same values after a round-trip through the Java ValueReader and ValueWriter" in {
    forAll(assertThatValueIsPreservedThroughJavaWriteAndRead _)
  }

  it should "preserve a specific StringVal that previously failed after a round-trip through the Java ValueReader and ValueWriter" in {
    assertThatValueIsPreservedThroughJavaWriteAndRead(StringVal("kyvmqzlbjivLqQFukljghxdowkcmjklgSeybdy"))
  }

  it should "preserve a specific DateVal created from an Instant that has millisecond accuracy after a round-trip through the Java ValueReader and ValueWriter" in {
    val instant   = Instant.parse("4000-11-03T20:17:29.57Z")
    val myDateVal = TimestampVal.fromInstant(instant)
    assertThatValueIsPreservedThroughJavaWriteAndRead(myDateVal)
  }

  "DecimalVal" should "reject a BigDecimal of an unscaled value with 33 bits..." in {
    DecimalVal.fromBigDecimal(BigDecimal(Int.MaxValue) + BigDecimal(1)) should be(None)
  }
  it should "reject a BigDecimal with a scale over octet size" in {
    DecimalVal.fromBigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(12345L), 1000)) should be(None)
  }

  // We need to wrap things in a dummy table because the method that would be
  // great to test with ValueReader, readFieldValue, is private, and so we
  // have to call the next best thing, readTable.
  private def wrapInDummyTable(value: AmqpFieldValue): TableVal =
    TableVal(Map(ShortString.unsafeOf("dummyKey") -> value))

  private def createWriterFromQueue(outputResults: collection.mutable.Queue[Byte]): ValueWriter =
    new ValueWriter({
      new DataOutputStream({
        new OutputStream {
          override def write(b: Int): Unit =
            outputResults.enqueue(b.toByte)
        }
      })
    })

  private def createReaderFromQueue(input: collection.mutable.Queue[Byte]): ValueReader = {
    val inputStream = new InputStream {
      override def read(): Int =
        try {
          val result = input.dequeue()
          // A signed -> unsigned conversion because bytes by default are
          // converted into signed ints, which is bad when the API of read
          // states that negative numbers indicate EOF...
          0Xff & result.toInt
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
    AmqpFieldValue.unsafeFromValueReaderOutput(readValue) should be(wrapInDummyTable(amqpHeaderVal))
  }
}