package com.github.gvolpe.fs2rabbit.json

import cats.effect.Effect
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model.{AmqpEnvelope, DeliveryTag}
import fs2.Pipe
import io.circe.parser.decode
import io.circe.{Decoder, Error}
import org.slf4j.LoggerFactory

import scala.language.higherKinds

/**
  * Stream-based Json Decoder that exposes only one method as a streaming transformation
  * using [[fs2.Pipe]] and depends on the Circe library.
  * */
object Fs2JsonDecoder {

  private val log = LoggerFactory.getLogger(getClass)

  /**
    * It tries to decode an [[AmqpEnvelope.payload]] into a case class determined by the parameter [A].
    *
    * For example:
    *
    * {{{
    * import fs2._
    *
    * val json = """ { "two": "the two" } """
    * val envelope = AmqpEnvelope(1, json, AmqpProperties.empty)
    *
    * val p = Stream(envelope).covary[IO] through jsonDecode[IO, Person]
    *
    * p.run.unsafeRunSync
    * }}}
    *
    * The result will be a tuple ([[Either[Error, A]], [[DeliveryTag]])
    * */
  def jsonDecode[F[_] : Effect, A : Decoder]: Pipe[F, AmqpEnvelope, (Either[Error, A], DeliveryTag)] =
    streamMsg =>
      for {
        amqpMsg <- streamMsg
        parsed  <- asyncF[F, Either[Error, A]](decode[A](amqpMsg.payload))
        _       <- asyncF[F, Unit](log.debug(s"Parsed: $parsed"))
      } yield (parsed, amqpMsg.deliveryTag)

}