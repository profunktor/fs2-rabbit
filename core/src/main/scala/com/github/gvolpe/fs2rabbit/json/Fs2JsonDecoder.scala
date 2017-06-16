package com.github.gvolpe.fs2rabbit.json

import cats.effect.Effect
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model.{AmqpEnvelope, DeliveryTag}
import fs2.Pipe
import io.circe.parser.decode
import io.circe.{Decoder, Error}
import org.slf4j.LoggerFactory

import scala.language.higherKinds

object Fs2JsonDecoder {

  private val log = LoggerFactory.getLogger(getClass)

  def jsonDecode[F[_], A : Decoder](implicit F: Effect[F]): Pipe[F, AmqpEnvelope, (Either[Error, A], DeliveryTag)] =
    streamMsg =>
      for {
        amqpMsg <- streamMsg
        parsed  <- asyncF[F, Either[Error, A]](decode[A](amqpMsg.payload))
        _       <- asyncF[F, Unit](log.debug(s"Parsed: $parsed"))
      } yield (parsed, amqpMsg.deliveryTag)

}