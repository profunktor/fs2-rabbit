package com.github.gvolpe.fs2rabbit.free

import cats.effect.Effect
import cats.~>
import com.github.gvolpe.fs2rabbit.Fs2Rabbit
import fs2._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

// TODO: Transformation type should be AMQPBrokerEffect ~> Pipe[F, ?, ?] but there's no way
// to combine it with an interpreter X ~> Stream[F, ?] because the types are different...
class BrokerEffectStreamInterpreter[F[_] : Effect](implicit ec: ExecutionContext) extends (AMQPBrokerEffect ~> Stream[F, ?]) {

  def apply[A](fa: AMQPBrokerEffect[A]): Stream[F, A] = fa match {
//    case CreateAckerConsumer(channel, queueName) =>
//      Fs2Rabbit.createAckerConsumer[F](channel, queueName)
    case CreatePublisher(channel, exchangeName, routingKey) =>
      Fs2Rabbit.createPublisherAlt[F](channel, exchangeName, routingKey)
  }

}
