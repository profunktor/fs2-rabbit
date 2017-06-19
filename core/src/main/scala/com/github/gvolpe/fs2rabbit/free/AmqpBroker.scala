package com.github.gvolpe.fs2rabbit.free

import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.{Channel, Connection}

import scala.language.higherKinds

sealed trait AMQPBroker[A]

case object CreateConnectionAndChannel extends AMQPBroker[(Connection, Channel)]

// TODO: Separate creation of acker and consumer from the Fs2Rabbit trait
//  final case class CreateAckerConsumer(channel: Channel,
//                                       queueName: QueueName) extends AMQPBroker[(AckResult, AmqpEnvelope)]

final case class CreateAutoAckConsumer(channel: Channel,
                                       queueName: QueueName) extends AMQPBroker[AmqpEnvelope]

// TODO: A publisher is a Sink so a natural transformation to Stream doesn't work, see options.
//final case class CreatePublisher(channel: Channel,
//                                 exchangeName: ExchangeName,
//                                 routingKey: RoutingKey) extends AMQPBroker[AmqpMessage[String]]

final case class DeclareExchange(channel: Channel,
                                 exchangeName: ExchangeName,
                                 exchangeType: ExchangeType) extends AMQPBroker[Exchange.DeclareOk]

final case class DeclareQueue(channel: Channel, queueName: QueueName) extends AMQPBroker[Queue.DeclareOk]

final case class BindQueue(channel: Channel,
                           queueName: QueueName,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey) extends AMQPBroker[Queue.BindOk]
