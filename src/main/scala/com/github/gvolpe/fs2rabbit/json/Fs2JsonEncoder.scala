package com.github.gvolpe.fs2rabbit.json

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.Fs2Utils.async
import com.github.gvolpe.fs2rabbit.model.AmqpMessage
import fs2.Pipe
import io.circe.Encoder
import io.circe.syntax._

object Fs2JsonEncoder {

  def jsonEncode[A : Encoder]: Pipe[IO, AmqpMessage[A], AmqpMessage[String]] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      json    <- async(amqpMsg.payload.asJson.noSpaces)
    } yield AmqpMessage(json, amqpMsg.properties)
  }

}
