package com.github.gvolpe.fs2rabbit.json

import cats.effect.Effect
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model.AmqpMessage
import fs2.Pipe
import io.circe.Encoder
import io.circe.syntax._

import scala.language.higherKinds

object Fs2JsonEncoder {

  def jsonEncode[F[_], A : Encoder](implicit F: Effect[F]): Pipe[F, AmqpMessage[A], AmqpMessage[String]] =
    streamMsg =>
      for {
        amqpMsg <- streamMsg
        json    <- asyncF[F, String](amqpMsg.payload.asJson.noSpaces)
      } yield AmqpMessage(json, amqpMsg.properties)

}
