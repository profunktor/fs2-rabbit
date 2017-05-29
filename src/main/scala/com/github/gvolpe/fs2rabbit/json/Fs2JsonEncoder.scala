package com.github.gvolpe.fs2rabbit.json

import com.github.gvolpe.fs2rabbit.Fs2Utils.async
import com.github.gvolpe.fs2rabbit.model.AmqpMessage
import fs2.{Pipe, Task}
import io.circe.Encoder
import io.circe.syntax._

object Fs2JsonEncoder {

  def jsonEncode[A : Encoder]: Pipe[Task, AmqpMessage[A], AmqpMessage[String]] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      json    <- async(amqpMsg.payload.asJson.noSpaces)
    } yield AmqpMessage(json, amqpMsg.properties)
  }

}
