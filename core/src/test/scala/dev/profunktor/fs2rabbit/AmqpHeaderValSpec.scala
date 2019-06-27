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
import dev.profunktor.fs2rabbit.model.{AmqpHeaderVal, ShortString}

import scala.math.BigDecimal.RoundingMode
import dev.profunktor.fs2rabbit.model.AmqpHeaderVal._
import org.scalatest.{Assertion, FlatSpecLike, Matchers}

class AmqpHeaderValSpec extends FlatSpecLike with Matchers with AmqpPropertiesArbitraries {

  it should "convert from and to Java primitive header values" in {
    val intVal    = IntVal(1)
    val longVal   = LongVal(2L)
    val stringVal = StringVal("hey")
    val arrayVal  = ArrayVal(Vector(IntVal(3), IntVal(2), IntVal(1)))

    AmqpHeaderVal.unsafeFrom(intVal.impure) should be(intVal)
    AmqpHeaderVal.unsafeFrom(longVal.impure) should be(longVal)
    AmqpHeaderVal.unsafeFrom(stringVal.impure) should be(stringVal)
    AmqpHeaderVal.unsafeFrom("fs2") should be(StringVal("fs2"))
    AmqpHeaderVal.unsafeFrom(arrayVal.impure) should be(arrayVal)
  }
  it should "preserve the same value after a round-trip through impure and from" in {
    forAll { amqpHeaderVal: AmqpHeaderVal =>
      AmqpHeaderVal.unsafeFrom(amqpHeaderVal.impure) == amqpHeaderVal
    }
  }

  // We need to wrap things in a dummy table because the method that would be
  // great to test with ValueReader, readFieldValue, is private, and so we
  // have to call the next best thing, readTable.
  private def wrapInDummyTable(value: AmqpHeaderVal): TableVal =
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

  private def assertThatValueIsPreservedThroughJavaWriteAndRead(amqpHeaderVal: AmqpHeaderVal): Assertion = {
    val outputResultsAsTable = collection.mutable.Queue.empty[Byte]
    val tableWriter          = createWriterFromQueue(outputResultsAsTable)
    val clippedAmqpHeaderVal = amqpHeaderVal match {
      case DecimalVal(bigDecimal) =>
        val upperLimit = BigDecimal(Short.MaxValue)
        // Because we can't encode BigDecimals that are too big or too precise...
        val clippedBigDecimal = (bigDecimal % upperLimit).setScale(2, RoundingMode.CEILING)
        DecimalVal.unsafeFromBigDecimal(clippedBigDecimal)

      case notBigDecimal => notBigDecimal
    }
    tableWriter.writeTable(
      wrapInDummyTable(clippedAmqpHeaderVal).impure
        .asInstanceOf[java.util.Map[String, AnyRef]]
    )

    val reader    = createReaderFromQueue(outputResultsAsTable)
    val readValue = reader.readTable()
    AmqpHeaderVal.unsafeFrom(readValue) should be(wrapInDummyTable(amqpHeaderVal))
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

}
