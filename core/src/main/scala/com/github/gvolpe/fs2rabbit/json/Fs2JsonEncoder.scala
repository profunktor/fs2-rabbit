package com.github.gvolpe.fs2rabbit.json

import cats.effect.Effect
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model.AmqpMessage
import fs2.Pipe
import io.circe.Encoder
import io.circe.syntax._

import scala.language.higherKinds

/**
  * Stream-based Json Encoder that exposes only one method as a streaming transformation
  * using [[fs2.Pipe]] and depends on the Circe library.
  * */
object Fs2JsonEncoder {

  /**
    * It tries to encode a given case class encapsulated in an  [[AmqpMessage]] into a
    * json string.
    *
    * For example:
    *
    * {{{
    * import fs2._
    *
    * val payload = Person("Sherlock", Address(212, "Baker St"))
    * val p = Stream(AmqpMessage(payload, AmqpProperties.empty)).covary[IO] through jsonEncode[IO, Person]
    *
    * p.run.unsafeRunSync
    * }}}
    *
    * The result will be an [[AmqpMessage]] of type [[String]]
    * */
  def jsonEncode[F[_] : Effect, A : Encoder]: Pipe[F, AmqpMessage[A], AmqpMessage[String]] =
    streamMsg =>
      for {
        amqpMsg <- streamMsg
        json    <- asyncF[F, String](amqpMsg.payload.asJson.noSpaces)
      } yield AmqpMessage(json, amqpMsg.properties)

}
