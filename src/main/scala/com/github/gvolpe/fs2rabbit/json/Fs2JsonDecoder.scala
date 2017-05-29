package com.github.gvolpe.fs2rabbit.json

import cats.data.Xor
import com.github.gvolpe.fs2rabbit.Fs2Utils.async
import com.github.gvolpe.fs2rabbit.model.{AmqpEnvelope, DeliveryTag}
import fs2.{Pipe, Task}
import io.circe.{Decoder, Error}
import io.circe.parser.decode
import org.slf4j.LoggerFactory

object Fs2JsonDecoder {

  private val log = LoggerFactory.getLogger(getClass)

  def jsonDecode[A : Decoder]: Pipe[Task, AmqpEnvelope, (Xor[Error, A], DeliveryTag)] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- async(log.info(s"Incoming Json: $amqpMsg"))
      parsed  <- async(decode[A](amqpMsg.payload))
      _       <- async(log.info(s"Parsed: $parsed"))
    } yield (parsed, amqpMsg.deliveryTag)
  }

}