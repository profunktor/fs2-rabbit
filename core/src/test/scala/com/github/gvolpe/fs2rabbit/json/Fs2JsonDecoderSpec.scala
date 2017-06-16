package com.github.gvolpe.fs2rabbit.json

import cats.effect.IO
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.json.Fs2JsonDecoder.jsonDecode
import com.github.gvolpe.fs2rabbit.model.{AmqpEnvelope, AmqpProperties}
import fs2._
import io.circe._
import io.circe.generic.auto._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpecLike, Matchers}
import scala.concurrent.duration._

class Fs2JsonDecoderSpec extends Fs2JsonDecoderFixture with FlatSpecLike with Matchers {

  behavior of "Fs2JsonDecoder"

  forAll(examples){ (description, json, decoder, expected) =>
    it should description in {
      val envelope = AmqpEnvelope(1, json, AmqpProperties.empty)

      val test = for {
        parsed          <- Stream(envelope).covary[IO] through decoder
        (validated, _)  = parsed
      } yield {
        validated should be (expected)
      }

      test.run.unsafeRunTimed(2.seconds)
    }
  }

  it should "fail decoding the wrong class" in {
    val json = """ { "two": "the two" } """
    val envelope = AmqpEnvelope(1, json, AmqpProperties.empty)

    val test = for {
      parsed          <- Stream(envelope).covary[IO] through jsonDecode[IO, Person]
      (validated, _)  = parsed
    } yield {
      validated shouldBe a[Left[_, Person]]
    }

    test.run.unsafeRunTimed(2.seconds)
  }

}

trait Fs2JsonDecoderFixture extends PropertyChecks {

  case class Address(number: Int, streetName: String)
  case class Person(name: String, address: Address)

  sealed trait Message extends Product with Serializable
  final case class One(one: String) extends Message
  final case class Two(two: String) extends Message
  final case class Three(three: Int) extends Message

  implicit def msgDecoder[A >: Message]: Decoder[A] =
    List[Decoder[A]](
      Decoder[One].widen,
      Decoder[Two].widen,
      Decoder[Three].widen
    ).reduceLeft(_ or _)

  val simpleJson: String =
    """
      |{
      |  "number": 212,
      |  "streetName": "Baker St"
      |}
    """.stripMargin

  val nestedJson: String =
    """
      |{
      |  "name": "Sherlock",
      |  "address": {
      |    "number": 212,
      |    "streetName": "Baker St"
      |  }
      |}
    """.stripMargin

  val examples = Table(
    ("description", "json", "clazz", "expected"),
    ("decode a simple case class", simpleJson, jsonDecode[IO, Address], Right(Address(212, "Baker St"))),
    ("decode a nested case class", nestedJson, jsonDecode[IO, Person], Right(Person("Sherlock", Address(212, "Baker St")))),
    ("decode an adt 1", """ { "one": "the one" } """, jsonDecode[IO, Message], Right(One("the one"))),
    ("decode an adt 2", """ { "two": "the two" } """, jsonDecode[IO, Message], Right(Two("the two")))
  )

}