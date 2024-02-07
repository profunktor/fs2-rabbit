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

package dev.profunktor.fs2rabbit.json

import dev.profunktor.fs2rabbit.model.{AmqpMessage, AmqpProperties}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class Fs2JsonEncoderSpec extends AnyFlatSpecLike with Matchers {

  case class Address(number: Int, streetName: String)
  case class Person(name: String, address: Address)

  private val fs2JsonEncoder = new Fs2JsonEncoder
  import fs2JsonEncoder.jsonEncode

  it should "encode a simple case class" in {
    val payload = Address(212, "Baker St")
    val encode  = jsonEncode[Address]

    encode(AmqpMessage(payload, AmqpProperties.empty)) should be(
      AmqpMessage(payload.asJson.noSpaces, AmqpProperties.empty)
    )
  }

  it should "encode a nested case class" in {
    val payload = Person("Sherlock", Address(212, "Baker St"))
    val encode  = jsonEncode[Person]
    encode(AmqpMessage(payload, AmqpProperties.empty)) should be(
      AmqpMessage(payload.asJson.noSpaces, AmqpProperties.empty)
    )
  }

  it should "encode a simple case class according to a custom printer" in {
    val payload = Address(212, "Baker St")

    val customEncode = new Fs2JsonEncoder(Printer.spaces4).jsonEncode[Address]

    customEncode(AmqpMessage(payload, AmqpProperties.empty)) should be(
      AmqpMessage(payload.asJson.printWith(Printer.spaces4), AmqpProperties.empty)
    )
  }

}
