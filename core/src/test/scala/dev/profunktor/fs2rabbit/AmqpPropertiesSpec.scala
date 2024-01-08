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

import java.util.Date

import com.rabbitmq.client.AMQP
import dev.profunktor.fs2rabbit.model.AmqpFieldValue._
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, AmqpProperties, DeliveryMode, ShortString}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}
import scodec.bits.ByteVector

class AmqpPropertiesSpec extends AnyFlatSpecLike with Matchers with AmqpPropertiesArbitraries {

  it should s"convert from and to Java AMQP.BasicProperties" in {
    forAll { (amqpProperties: AmqpProperties) =>
      val basicProps = amqpProperties.asBasicProps
      AmqpProperties.unsafeFrom(basicProps) should be(amqpProperties)
    }
  }

  it should "create an empty amqp properties" in {
    AmqpProperties.empty should be(
      AmqpProperties(
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        Map.empty[String, AmqpFieldValue]
      )
    )
  }

  it should "handle null values in Java AMQP.BasicProperties" in {
    val basic = new AMQP.BasicProperties()
    AmqpProperties.unsafeFrom(basic) should be(AmqpProperties.empty)
  }

}

trait AmqpPropertiesArbitraries extends PropertyChecks {

  implicit val bigDecimalVal: Arbitrary[DecimalVal] = Arbitrary[DecimalVal] {
    for {
      unscaledValue <- arbitrary[Int]
      scale         <- Gen.choose(0, 255)
    } yield {
      val javaBigDecimal = new java.math.BigDecimal(BigInt(unscaledValue).bigInteger, scale)
      DecimalVal.unsafeFrom(BigDecimal(javaBigDecimal))
    }
  }

  implicit val dateVal: Arbitrary[TimestampVal] = Arbitrary[TimestampVal] {
    arbitrary[Date].map(TimestampVal.from)
  }

  private def modTruncateString(str: String): ShortString = {
    val newLength = str.length % (ShortString.MaxByteLength + 1)
    ShortString.unsafeFrom(str.substring(newLength))
  }

  def tableVal(maxDepth: Int): Arbitrary[TableVal] = Arbitrary {
    for {
      keys            <- arbitrary[List[String]]
      keysWithValueGen = keys.map(key => amqpHeaderVal(maxDepth).arbitrary.map(modTruncateString(key) -> _))
      keyValues       <- Gen.sequence[List[(ShortString, AmqpFieldValue)], (ShortString, AmqpFieldValue)](keysWithValueGen)
    } yield TableVal(keyValues.toMap)
  }

  implicit val byteVal: Arbitrary[ByteVal] = Arbitrary {
    arbitrary[Byte].map(ByteVal.apply)
  }

  implicit val doubleVal: Arbitrary[DoubleVal] = Arbitrary {
    arbitrary[Double].map(DoubleVal.apply)
  }

  implicit val floatVal: Arbitrary[FloatVal] = Arbitrary {
    arbitrary[Float].map(FloatVal.apply)
  }

  implicit val shortVal: Arbitrary[ShortVal] = Arbitrary {
    arbitrary[Short].map(ShortVal.apply)
  }

  implicit val byteArrayVal: Arbitrary[ByteArrayVal] = Arbitrary {
    arbitrary[Array[Byte]].map(xs => ByteArrayVal(ByteVector(xs)))
  }

  implicit val booleanVal: Arbitrary[BooleanVal] = Arbitrary {
    arbitrary[Boolean].map(BooleanVal.apply)
  }

  implicit val intVal: Arbitrary[IntVal] = Arbitrary[IntVal] {
    Gen.posNum[Int].flatMap(x => IntVal(x))
  }

  implicit val longVal: Arbitrary[LongVal] = Arbitrary[LongVal] {
    Gen.posNum[Long].flatMap(x => LongVal(x))
  }

  implicit val stringVal: Arbitrary[StringVal] = Arbitrary[StringVal] {
    Gen.alphaStr.flatMap(x => StringVal(x))
  }

  def arrayVal(maxDepth: Int): Arbitrary[ArrayVal] = Arbitrary {
    implicit val implicitAmqpHeaderVal: Arbitrary[AmqpFieldValue] = amqpHeaderVal(maxDepth)
    arbitrary[Vector[AmqpFieldValue]].map(ArrayVal.apply)
  }

  implicit val nullVal: Arbitrary[NullVal.type] = Arbitrary {
    Gen.const(NullVal)
  }

  implicit val implicitAmqpHeaderVal: Arbitrary[AmqpFieldValue] = Arbitrary {
    // Cap it at 2 so that we don't have Stack Overflows/long test times
    amqpHeaderVal(2).arbitrary
  }

  def amqpHeaderVal(maxDepth: Int): Arbitrary[AmqpFieldValue] = Arbitrary[AmqpFieldValue] {
    val nonRecursiveGenerators = List(
      bigDecimalVal.arbitrary,
      dateVal.arbitrary,
      byteVal.arbitrary,
      doubleVal.arbitrary,
      floatVal.arbitrary,
      shortVal.arbitrary,
      byteArrayVal.arbitrary,
      booleanVal.arbitrary,
      intVal.arbitrary,
      longVal.arbitrary,
      stringVal.arbitrary,
      nullVal.arbitrary
    )

    if (maxDepth <= 0) {
      // This is because Gen.oneOf is overloaded and we need to access its three-argument version
      Gen.oneOf(nonRecursiveGenerators(0), nonRecursiveGenerators(1), nonRecursiveGenerators.drop(2): _*)
    } else {
      val allGenerators = tableVal(maxDepth - 1).arbitrary :: arrayVal(maxDepth - 1).arbitrary :: nonRecursiveGenerators
      Gen.lzy(
        Gen.oneOf(allGenerators(0), allGenerators(1), allGenerators.drop(2): _*)
      )
    }
  }

  private val headersGen: Gen[(String, AmqpFieldValue)] = for {
    key   <- Gen.alphaStr
    value <- arbitrary[AmqpFieldValue]
  } yield (key, value)

  implicit val amqpProperties: Arbitrary[AmqpProperties] = Arbitrary[AmqpProperties] {
    for {
      contentType     <- Gen.option(Gen.alphaStr)
      contentEncoding <- Gen.option(Gen.alphaStr)
      priority        <- Gen.option(Gen.posNum[Int])
      deliveryMode    <- Gen.option(Gen.oneOf(1, 2))
      correlationId   <- Gen.option(Gen.alphaNumStr)
      messageId       <- Gen.option(Gen.alphaNumStr)
      messageType     <- Gen.option(Gen.alphaStr)
      userId          <- Gen.option(Gen.alphaNumStr)
      appId           <- Gen.option(Gen.alphaNumStr)
      expiration      <- Gen.option(Gen.alphaNumStr)
      replyTo         <- Gen.option(Gen.alphaNumStr)
      clusterId       <- Gen.option(Gen.alphaNumStr)
      timestamp       <- Gen.option(dateVal.arbitrary.map(_.instantWithOneSecondAccuracy))
      headers         <- Gen.mapOf[String, AmqpFieldValue](headersGen)
    } yield AmqpProperties(
      contentType,
      contentEncoding,
      priority,
      deliveryMode.map(DeliveryMode.from),
      correlationId,
      messageId,
      messageType,
      userId,
      appId,
      expiration,
      replyTo,
      clusterId,
      timestamp,
      headers
    )
  }

}
