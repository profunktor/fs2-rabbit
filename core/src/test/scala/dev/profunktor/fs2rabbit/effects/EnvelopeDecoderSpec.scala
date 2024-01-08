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

package dev.profunktor.fs2rabbit.effects

import java.nio.charset.StandardCharsets

import cats.data.EitherT
import cats.effect.{IO, SyncIO}
import cats.instances.either._
import cats.instances.try_._
import dev.profunktor.fs2rabbit.model.{AmqpEnvelope, AmqpProperties, DeliveryTag, ExchangeName, RoutingKey}
import org.scalatest.funsuite.AsyncFunSuite

import scala.util.Try

import cats.effect.unsafe.implicits.global

class EnvelopeDecoderSpec extends AsyncFunSuite {

  // Available instances of EnvelopeDecoder for any ApplicativeError[F, Throwable]
  EnvelopeDecoder[Either[Throwable, *], String]
  EnvelopeDecoder[SyncIO, String]
  EnvelopeDecoder[EitherT[IO, String, *], String]
  EnvelopeDecoder[Try, String]

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

}
