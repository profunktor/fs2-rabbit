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

import java.util.Date

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.impl.LongStringHelper
import dev.profunktor.fs2rabbit.model.AmqpHeaderVal._
import dev.profunktor.fs2rabbit.model.{AmqpHeaderVal, AmqpProperties, DeliveryMode}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._
import org.scalatest.{FlatSpecLike, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class AmqpPropertiesSpec extends FlatSpecLike with Matchers with AmqpPropertiesArbitraries {

  it should s"convert from and to Java AMQP.BasicProperties" in {
    forAll { amqpProperties: AmqpProperties =>
      val basicProps = amqpProperties.asBasicProps
      AmqpProperties.from(basicProps) should be(amqpProperties)
    }
  }

  it should "create an empty amqp properties" in {
    AmqpProperties.empty should be(
      AmqpProperties(None,
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
                     Map.empty[String, AmqpHeaderVal]))
  }

  it should "handle null values in Java AMQP.BasicProperties" in {
    val basic = new AMQP.BasicProperties()
    AmqpProperties.from(basic) should be(AmqpProperties.empty)
  }

}

trait AmqpPropertiesArbitraries extends PropertyChecks {

  implicit val bigDecimalVal: Arbitrary[BigDecimalVal] = Arbitrary[BigDecimalVal] {
    // Write it out explicitly because BigDecimal has conflicting instances
    arbitrary[BigDecimal].map(x => BigDecimalVal(x))
  }

  implicit val dateVal: Arbitrary[DateVal] = Arbitrary[DateVal] {
    arbitrary[Date].map(DateVal.apply)
  }

  def tableVal(maxDepth: Int): Arbitrary[TableVal] = Arbitrary {
    for {
      keys             <- arbitrary[List[String]]
      keysWithValueGen = keys.map(key => amqpHeaderVal(maxDepth).arbitrary.map(key -> _))
      keyValues        <- Gen.sequence[List[(String, AmqpHeaderVal)], (String, AmqpHeaderVal)](keysWithValueGen)
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
    arbitrary[Array[Byte]].map(ByteArrayVal.apply)
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

  implicit val longStringVal: Arbitrary[LongStringVal] = Arbitrary {
    arbitrary[String].map(str => LongStringVal(LongStringHelper.asLongString(str)))
  }

  def arrayVal(maxDepth: Int): Arbitrary[ArrayVal] = Arbitrary {
    implicit val implicitAmqpHeaderVal: Arbitrary[AmqpHeaderVal] = amqpHeaderVal(maxDepth)
    arbitrary[Vector[AmqpHeaderVal]].map(ArrayVal.apply)
  }

  implicit val nullVal: Arbitrary[NullVal.type] = Arbitrary {
    Gen.const(NullVal)
  }

  implicit val implicitAmqpHeaderVal: Arbitrary[AmqpHeaderVal] = Arbitrary {
    amqpHeaderVal(2).arbitrary
  }

  def amqpHeaderVal(maxDepth: Int): Arbitrary[AmqpHeaderVal] = Arbitrary[AmqpHeaderVal] {
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
      longStringVal.arbitrary,
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

  private val headersGen: Gen[(String, AmqpHeaderVal)] = for {
    key   <- Gen.alphaStr
    value <- arbitrary[AmqpHeaderVal]
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
      headers         <- Gen.mapOf[String, AmqpHeaderVal](headersGen)
    } yield
      AmqpProperties(
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
        headers
      )
  }

}
