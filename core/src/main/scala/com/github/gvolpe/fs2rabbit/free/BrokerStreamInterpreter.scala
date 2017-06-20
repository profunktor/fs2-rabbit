package com.github.gvolpe.fs2rabbit.free

import cats.effect.Effect
import cats.~>
import com.github.gvolpe.fs2rabbit.Fs2Rabbit
import fs2._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class BrokerStreamInterpreter[F[_] : Effect](implicit ec: ExecutionContext) extends (AMQPBroker ~> Stream[F, ?]) {

  def apply[A](fa: AMQPBroker[A]): Stream[F, A] = fa match {
    case CreateConnectionAndChannel =>
      Fs2Rabbit.createConnectionChannel[F]()
    case CreateAutoAckConsumer(channel, queueName) =>
      Fs2Rabbit.createAutoAckConsumer[F](channel, queueName)
    case DeclareExchange(channel, exchangeName, exchangeType) =>
      Fs2Rabbit.declareExchange[F](channel, exchangeName, exchangeType)
    case DeclareQueue(channel, queueName) =>
      Fs2Rabbit.declareQueue(channel, queueName)
    case BindQueue(channel, queueName, exchangeName, routingKey) =>
      Fs2Rabbit.bindQueue[F](channel, queueName, exchangeName, routingKey)
  }

}
