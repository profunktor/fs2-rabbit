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

package dev.profunktor.fs2rabbit.effects

import java.nio.charset.StandardCharsets

import cats.data.EitherT
import cats.effect.{IO, SyncIO}
import cats.instances.either.*
import cats.instances.try_.*
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.*
import dev.profunktor.fs2rabbit.model.{AmqpEnvelope, AmqpProperties, DeliveryTag, ExchangeName, Headers, RoutingKey}
import org.scalatest.funsuite.AsyncFunSuite

import scala.util.Try

import cats.effect.unsafe.implicits.global

class EnvelopeDecoderSpec extends AsyncFunSuite {

  import EnvelopeDecoder.*
  // Available instances of EnvelopeDecoder for any ApplicativeError[F, Throwable]
  EnvelopeDecoder[Either[Throwable, *], String]
  EnvelopeDecoder[SyncIO, String]
  EnvelopeDecoder[EitherT[IO, String, *], String]
  EnvelopeDecoder[Try, String]
  EnvelopeDecoder[Try, Option[String]]
  EnvelopeDecoder[Try, Either[Throwable, String]]

  test("should decode a UTF-8 string") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_8)

    EnvelopeDecoder[IO, String]
      .run(
        AmqpEnvelope(DeliveryTag(0L), raw, AmqpProperties.empty, ExchangeName("test"), RoutingKey("test.route"), false)
      )
      .flatMap { result =>
        IO(assert(result == msg))
      }
      .unsafeToFuture()
  }

  test("should decode payload with the given content encoding") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_8)

    EnvelopeDecoder[IO, String]
      .run(
        AmqpEnvelope(
          DeliveryTag(0L),
          raw,
          AmqpProperties.empty.copy(contentEncoding = Some("UTF-16")),
          ExchangeName("test"),
          RoutingKey("test.route"),
          false
        )
      )
      .flatMap { result =>
        IO(assert(result != msg))
      }
      .unsafeToFuture()
  }

  test("should decode a UTF-16 string into a UTF-8 (default)") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_16)

    EnvelopeDecoder[IO, String]
      .run(
        AmqpEnvelope(DeliveryTag(0L), raw, AmqpProperties.empty, ExchangeName("test"), RoutingKey("test.route"), false)
      )
      .flatMap { result =>
        IO(assert(result != msg))
      }
      .unsafeToFuture()
  }

  test("should decode a UTF-8 string - Attempt") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_8)

    EnvelopeDecoder[IO, Either[Throwable, String]]
      .run(
        AmqpEnvelope(DeliveryTag(0L), raw, AmqpProperties.empty, ExchangeName("test"), RoutingKey("test.route"), false)
      )
      .flatMap { result =>
        IO(assert(result == Right(msg)))
      }
      .unsafeToFuture()
  }

  test("should decode a UTF-8 string - Attempt option") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_8)

    EnvelopeDecoder[IO, Option[String]]
      .run(
        AmqpEnvelope(DeliveryTag(0L), raw, AmqpProperties.empty, ExchangeName("test"), RoutingKey("test.route"), false)
      )
      .flatMap { result =>
        IO(assert(result == Option(msg)))
      }
      .unsafeToFuture()
  }

  test("properties should extract AmqpProperties from envelope") {
    val props    = AmqpProperties.empty.copy(contentType = Some("application/json"))
    val envelope =
      AmqpEnvelope(DeliveryTag(0L), Array.emptyByteArray, props, ExchangeName("test"), RoutingKey("test.route"), false)

    EnvelopeDecoder
      .properties[IO]
      .run(envelope)
      .flatMap { result =>
        IO(assert(result.contentType == Some("application/json")))
      }
      .unsafeToFuture()
  }

  test("payload should extract raw bytes from envelope") {
    val data     = Array[Byte](1, 2, 3, 4)
    val envelope =
      AmqpEnvelope(DeliveryTag(0L), data, AmqpProperties.empty, ExchangeName("test"), RoutingKey("rk"), false)

    EnvelopeDecoder
      .payload[IO]
      .run(envelope)
      .flatMap { result =>
        IO(assert(result.sameElements(data)))
      }
      .unsafeToFuture()
  }

  test("envelope field extractors should extract routingKey, exchangeName, and redelivered") {
    val envelope = AmqpEnvelope(
      DeliveryTag(0L),
      Array.emptyByteArray,
      AmqpProperties.empty,
      ExchangeName("my-exchange"),
      RoutingKey("my.route"),
      true
    )

    (for {
      rk <- EnvelopeDecoder.routingKey[IO].run(envelope)
      ex <- EnvelopeDecoder.exchangeName[IO].run(envelope)
      rd <- EnvelopeDecoder.redelivered[IO].run(envelope)
    } yield {
      assert(rk == RoutingKey("my.route"))
      assert(ex == ExchangeName("my-exchange"))
      assert(rd == true)
    }).unsafeToFuture()
  }

  test("headers should extract headers from envelope") {
    val hdrs     = Headers("x-custom" -> IntVal(42))
    val props    = AmqpProperties.empty.copy(headers = hdrs)
    val envelope =
      AmqpEnvelope(DeliveryTag(0L), Array.emptyByteArray, props, ExchangeName("ex"), RoutingKey("rk"), false)

    EnvelopeDecoder
      .headers[IO]
      .run(envelope)
      .flatMap { result =>
        IO(assert(result.toMap.contains("x-custom")))
      }
      .unsafeToFuture()
  }

  test("header should extract specific header value") {
    val hdrs     = Headers("x-test" -> StringVal("hello"))
    val props    = AmqpProperties.empty.copy(headers = hdrs)
    val envelope =
      AmqpEnvelope(DeliveryTag(0L), Array.emptyByteArray, props, ExchangeName("ex"), RoutingKey("rk"), false)

    EnvelopeDecoder
      .header[IO]("x-test")
      .run(envelope)
      .flatMap { result =>
        IO(assert(result == StringVal("hello")))
      }
      .unsafeToFuture()
  }

  test("header should fail for missing header") {
    val props    = AmqpProperties.empty
    val envelope =
      AmqpEnvelope(DeliveryTag(0L), Array.emptyByteArray, props, ExchangeName("ex"), RoutingKey("rk"), false)

    EnvelopeDecoder
      .header[IO]("missing-header")
      .run(envelope)
      .attempt
      .flatMap { result =>
        IO(assert(result.isLeft))
      }
      .unsafeToFuture()
  }

  test("headerAs should decode header to typed value") {
    val hdrs     = Headers("x-count" -> IntVal(100))
    val props    = AmqpProperties.empty.copy(headers = hdrs)
    val envelope =
      AmqpEnvelope(DeliveryTag(0L), Array.emptyByteArray, props, ExchangeName("ex"), RoutingKey("rk"), false)

    EnvelopeDecoder
      .headerAs[IO, Int]("x-count")
      .run(envelope)
      .flatMap { result =>
        IO(assert(result == 100))
      }
      .unsafeToFuture()
  }

  test("optHeader and optHeaderAs should return Some for existing and None for missing headers") {
    val hdrs          = Headers("x-opt" -> StringVal("value"), "x-typed" -> LongVal(999L))
    val props         = AmqpProperties.empty.copy(headers = hdrs)
    val withHeaders   =
      AmqpEnvelope(DeliveryTag(0L), Array.emptyByteArray, props, ExchangeName("ex"), RoutingKey("rk"), false)
    val emptyEnvelope = AmqpEnvelope(
      DeliveryTag(0L),
      Array.emptyByteArray,
      AmqpProperties.empty,
      ExchangeName("ex"),
      RoutingKey("rk"),
      false
    )

    (for {
      existing      <- EnvelopeDecoder.optHeader[IO]("x-opt").run(withHeaders)
      missing       <- EnvelopeDecoder.optHeader[IO]("missing").run(emptyEnvelope)
      typedExisting <- EnvelopeDecoder.optHeaderAs[IO, Long]("x-typed").run(withHeaders)
      typedMissing  <- EnvelopeDecoder.optHeaderAs[IO, Long]("missing").run(emptyEnvelope)
    } yield {
      assert(existing == Some(StringVal("value")))
      assert(missing.isEmpty)
      assert(typedExisting == Some(999L))
      assert(typedMissing.isEmpty)
    }).unsafeToFuture()
  }

}
