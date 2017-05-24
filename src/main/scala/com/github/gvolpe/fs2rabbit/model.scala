package com.github.gvolpe.fs2rabbit

import fs2.{Sink, Stream, Task}

object model {

  type ExchangeName   = String
  type QueueName      = String
  type RoutingKey     = String
  type DeliveryTag    = Long

  object ExchangeType extends Enumeration {
    type ExchangeType = Value
    val Direct, FanOut, Headers, Topic = Value
  }

  sealed trait AckResult extends Product with Serializable
  final case class Ack(deliveryTag: DeliveryTag) extends AckResult
  final case class NAck(deliveryTag: DeliveryTag) extends AckResult

  type StreamAcker          = Sink[Task, AckResult]
  type StreamConsumer       = Stream[Task, AmqpEnvelope]
  type StreamAckerConsumer  = (StreamAcker, StreamConsumer)
  type StreamPublisher      = Sink[Task, String]

  case class AmqpEnvelope(deliveryTag: DeliveryTag, payload: String)
  case class AmqpMessage(payload: String, headers: Map[String, AnyRef])

}
