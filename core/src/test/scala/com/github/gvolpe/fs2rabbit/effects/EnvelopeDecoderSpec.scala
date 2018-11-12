package com.github.gvolpe.fs2rabbit.effects

import java.nio.charset.StandardCharsets

import cats.data.EitherT
import cats.effect.{IO, SyncIO}
import cats.instances.either._
import cats.instances.try_._
import com.github.gvolpe.fs2rabbit.model.AmqpProperties
import org.scalatest.AsyncFunSuite

import scala.util.Try

class EnvelopeDecoderSpec extends AsyncFunSuite {

  // Available instances of EnvelopeDecoder for any ApplicativeError[F, Throwable]
  EnvelopeDecoder[Either[Throwable, ?], String]
  EnvelopeDecoder[SyncIO, String]
  EnvelopeDecoder[EitherT[IO, String, ?], String]
  EnvelopeDecoder[Try, String]

  test("should decode a UTF-8 string") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_8)

    EnvelopeDecoder[IO, String]
      .decode(raw, AmqpProperties.empty)
      .flatMap { result =>
        IO(assert(result == msg))
      }
      .unsafeToFuture()
  }

  test("should decode payload with the given content encoding") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_8)

    EnvelopeDecoder[IO, String]
      .decode(raw, AmqpProperties.empty.copy(contentEncoding = Some("UTF-16")))
      .flatMap { result =>
        IO(assert(result != msg))
      }
      .unsafeToFuture()
  }

  test("should decode a UTF-16 string into a UTF-8 (default)") {
    val msg = "hello world!"
    val raw = msg.getBytes(StandardCharsets.UTF_16)

    EnvelopeDecoder[IO, String]
      .decode(raw, AmqpProperties.empty)
      .flatMap { result =>
        IO(assert(result != msg))
      }
      .unsafeToFuture()
  }

}
