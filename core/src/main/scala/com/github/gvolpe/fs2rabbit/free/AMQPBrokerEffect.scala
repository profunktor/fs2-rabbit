package com.github.gvolpe.fs2rabbit.free

import cats.free.{Free, Inject}
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel

import scala.language.higherKinds

sealed trait AMQPBrokerEffect[A]

// TODO: Separate creation of acker and consumer from the Fs2Rabbit trait
final case class CreateAckerConsumer(channel: Channel,
                                     queueName: QueueName) extends AMQPBrokerEffect[(AckResult, AmqpEnvelope)]

final case class CreatePublisher(channel: Channel,
                                 exchangeName: ExchangeName,
                                 routingKey: RoutingKey) extends AMQPBrokerEffect[AmqpMessage[String] => Unit]

class AMQPBrokerEffects[F[_]](implicit I: Inject[AMQPBrokerEffect, F]) {
  def createAckerConsumer(channel: Channel, queueName: QueueName): Free[F, (AckResult, AmqpEnvelope)] =
    Free.inject[AMQPBrokerEffect, F](CreateAckerConsumer(channel, queueName))

  def createPublisher(channel: Channel, exchangeName: ExchangeName, routingKey: RoutingKey): Free[F, AmqpMessage[String] => Unit] =
    Free.inject[AMQPBrokerEffect, F](CreatePublisher(channel, exchangeName, routingKey))
}

object AMQPBrokerEffects {
  implicit def effects[F[_]](implicit I: Inject[AMQPBrokerEffect, F]): AMQPBrokerEffects[F] = new AMQPBrokerEffects[F]
}