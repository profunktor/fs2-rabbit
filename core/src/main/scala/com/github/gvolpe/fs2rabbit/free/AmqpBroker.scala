package com.github.gvolpe.fs2rabbit.free

import cats.free.{Free, Inject}
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.{Channel, Connection}

import scala.language.higherKinds

sealed trait AMQPBroker[A]

case object CreateConnectionAndChannel extends AMQPBroker[(Connection, Channel)]

final case class CreateAutoAckConsumer(channel: Channel,
                                       queueName: QueueName) extends AMQPBroker[AmqpEnvelope]

final case class DeclareExchange(channel: Channel,
                                 exchangeName: ExchangeName,
                                 exchangeType: ExchangeType) extends AMQPBroker[Exchange.DeclareOk]

final case class DeclareQueue(channel: Channel, queueName: QueueName) extends AMQPBroker[Queue.DeclareOk]

final case class BindQueue(channel: Channel,
                           queueName: QueueName,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey) extends AMQPBroker[Queue.BindOk]

class AMQPBrokerService[F[_]](implicit I: Inject[AMQPBroker, F]) {
  def createConnectionAndChannel: Free[F, (Connection, Channel)] =
    Free.inject[AMQPBroker, F](CreateConnectionAndChannel)

  def createAutoAckConsumer(channel: Channel, queueName: QueueName): Free[F, AmqpEnvelope] =
    Free.inject[AMQPBroker, F](CreateAutoAckConsumer(channel, queueName))

  def declareExchange(channel: Channel, exchangeName: ExchangeName, exchangeType: ExchangeType): Free[F, Exchange.DeclareOk] =
    Free.inject[AMQPBroker, F](DeclareExchange(channel, exchangeName, exchangeType))

  def declareQueue(channel: Channel, queueName: QueueName): Free[F, Queue.DeclareOk] =
    Free.inject[AMQPBroker, F](DeclareQueue(channel, queueName))

  def bindQueue(channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey): Free[F, Queue.BindOk] =
    Free.inject[AMQPBroker, F](BindQueue(channel, queueName, exchangeName, routingKey))

}

object AMQPBrokerService {
  implicit def service[F[_]](implicit I: Inject[AMQPBroker, F]): AMQPBrokerService[F] = new AMQPBrokerService[F]
}