package com.gvolpe.fs2rabbit

import fs2.{Sink, Stream, Task}

object model {

  type ExchangeName   = String
  type QueueName      = String
  type RoutingKey     = String
  type DeliveryTag    = Long

  sealed trait AckResult extends Product with Serializable
  final case class Ack(deliveryTag: DeliveryTag) extends AckResult
  final case class NAck(deliveryTag: DeliveryTag) extends AckResult

  type AckerConsumer = (Stream[Task, AmqpMessage], Sink[Task, AckResult])

  case class AmqpMessage(deliveryTag: DeliveryTag, payload: String)

}
