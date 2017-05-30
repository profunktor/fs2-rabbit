package com.github.gvolpe.fs2rabbit.json

import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder.jsonEncode
import com.github.gvolpe.fs2rabbit.model.{AmqpMessage, AmqpProperties}
import fs2._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{FlatSpecLike, Matchers}

class Fs2JsonEncoderSpec extends FlatSpecLike with Matchers {

  behavior of "Fs2JsonEncoder"

  case class Address(number: Int, streetName: String)
  case class Person(name: String, address: Address)

  it should "encode a simple case class" in {
   val payload = Address(212, "Baker St")
   val test = for {
     json <- Stream(AmqpMessage(payload, AmqpProperties.empty)) through jsonEncode[Address]
   } yield {
     json should be (AmqpMessage(payload.asJson.noSpaces, AmqpProperties.empty))
   }

   test.run.unsafeRun()
  }

  it should "encode a nested case class" in {
    val payload = Person("Sherlock", Address(212, "Baker St"))
    val test = for {
      json <- Stream(AmqpMessage(payload, AmqpProperties.empty)) through jsonEncode[Person]
    } yield {
      json should be (AmqpMessage(payload.asJson.noSpaces, AmqpProperties.empty))
    }

    test.run.unsafeRun()
  }

}