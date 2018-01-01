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

package com.github.gvolpe.fs2rabbit.json

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.model.{AmqpMessage, AmqpProperties}
import fs2._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{FlatSpecLike, Matchers}
import scala.concurrent.duration._

class Fs2JsonEncoderSpec extends FlatSpecLike with Matchers {

  import com.github.gvolpe.fs2rabbit.instances.streameval._

  case class Address(number: Int, streetName: String)
  case class Person(name: String, address: Address)

  private val fs2JsonEncoder = new Fs2JsonEncoder[IO]
  import fs2JsonEncoder.jsonEncode

  it should "encode a simple case class" in {
    val payload = Address(212, "Baker St")
    val test = for {
      json <- Stream(AmqpMessage(payload, AmqpProperties.empty)).covary[IO] through jsonEncode[Address]
    } yield {
      json should be (AmqpMessage(payload.asJson.noSpaces, AmqpProperties.empty))
    }

    test.run.unsafeRunTimed(2.seconds)
  }

  it should "encode a nested case class" in {
    val payload = Person("Sherlock", Address(212, "Baker St"))
    val test = for {
      json <- Stream(AmqpMessage(payload, AmqpProperties.empty)).covary[IO] through jsonEncode[Person]
    } yield {
      json should be (AmqpMessage(payload.asJson.noSpaces, AmqpProperties.empty))
    }

    test.run.unsafeRunTimed(2.seconds)
  }

}