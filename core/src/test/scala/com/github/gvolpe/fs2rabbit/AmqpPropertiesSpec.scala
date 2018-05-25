/*
 * Copyright 2017 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit

import com.github.gvolpe.fs2rabbit.model.AmqpHeaderVal._
import com.github.gvolpe.fs2rabbit.model.{AmqpHeaderVal, AmqpProperties}
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.{FlatSpecLike, Matchers}
import com.rabbitmq.client.AMQP
import org.scalatest.prop.PropertyChecks

class AmqpPropertiesSpec extends FlatSpecLike with Matchers with AmqpPropertiesArbitraries {

  forAll { (amqpProperties: AmqpProperties) =>
    it should s"convert from and to Java AMQP.BasicProperties for $amqpProperties" in {
      val basicProps = amqpProperties.asBasicProps
      AmqpProperties.from(basicProps) should be(amqpProperties)
    }
  }

  it should "create an empty amqp properties" in {
    AmqpProperties.empty should be(AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal]))
  }

  it should "handle null values in Java AMQP.BasicProperties" in {
    val basic = new AMQP.BasicProperties()
    AmqpProperties.from(basic) should be(AmqpProperties.empty)
  }

}

trait AmqpPropertiesArbitraries extends PropertyChecks {

  implicit val intVal: Arbitrary[IntVal] = Arbitrary[IntVal] {
    Gen.posNum[Int].flatMap(x => IntVal(x))
  }

  implicit val longVal: Arbitrary[LongVal] = Arbitrary[LongVal] {
    Gen.posNum[Long].flatMap(x => LongVal(x))
  }

  implicit val stringVal: Arbitrary[StringVal] = Arbitrary[StringVal] {
    Gen.alphaStr.flatMap(x => StringVal(x))
  }

  implicit val amqpHeaderVal: Arbitrary[AmqpHeaderVal] = Arbitrary[AmqpHeaderVal] {
    Gen.oneOf(arbitrary[IntVal], arbitrary[LongVal], arbitrary[StringVal])
  }

  private val headersGen: Gen[(String, AmqpHeaderVal)] = for {
    key   <- Gen.alphaStr
    value <- arbitrary[AmqpHeaderVal]
  } yield (key, value)

  implicit val amqpProperties: Arbitrary[AmqpProperties] = Arbitrary[AmqpProperties] {
    for {
      contentType     <- Gen.option(Gen.alphaStr)
      contentEncoding <- Gen.option(Gen.alphaStr)
      headers         <- Gen.mapOf[String, AmqpHeaderVal](headersGen)
    } yield AmqpProperties(contentType, contentEncoding, headers)
  }

}
