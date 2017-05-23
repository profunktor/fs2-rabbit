package com.gvolpe.fs2rabbit.parser

import cats.data.Xor
import com.gvolpe.fs2rabbit.Fs2Utils.async
import com.gvolpe.fs2rabbit.model.{AmqpMessage, DeliveryTag}
import fs2.{Pipe, Task}
import io.circe.{Decoder, Error}
import io.circe.parser.decode
import org.slf4j.LoggerFactory

object Fs2JsonParser {

  private val log = LoggerFactory.getLogger(getClass)

  def jsonParser[A : Decoder]: Pipe[Task, AmqpMessage, (Xor[Error, A], DeliveryTag)] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- async(log.info(s"Incoming Json: $amqpMsg"))
      parsed  <- async(decode[A](amqpMsg.payload))
      _       <- async(log.info(s"Parsed: $parsed"))
    } yield (parsed, amqpMsg.deliveryTag)
  }

}