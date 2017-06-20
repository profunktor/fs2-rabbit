package com.github.gvolpe.fs2rabbit.free

import cats.~>
import cats.Monad
import cats.data.Coproduct
import cats.effect.IO
import cats.free.Free
import com.github.gvolpe.fs2rabbit.model._
import fs2.Stream

import scala.language.higherKinds

object FreeExample extends App {

  type AMQP[A] = Coproduct[AMQPBroker, AMQPBrokerEffect, A]

  val queueName     = QueueName("testQ")
  val exchangeName  = ExchangeName("testEX")
  val routingKey    = RoutingKey("testRK")

  def createConsumer(implicit B: AMQPBrokerService[AMQP], E: AMQPBrokerEffects[AMQP]): Free[AMQP, AmqpEnvelope] =
    for {
      connAndChannel  <- B.createConnectionAndChannel
      (_, channel)    = connAndChannel
      _               <- B.declareQueue(channel, queueName)
      _               <- B.declareExchange(channel, exchangeName, ExchangeType.Topic)
      _               <- B.bindQueue(channel, queueName, exchangeName, routingKey)
      consumer        <- B.createAutoAckConsumer(channel, queueName)
    } yield consumer

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  implicit val streamMonad = new Monad[Stream[IO, ?]] {
    def pure[A](x: A): Stream[IO, A] = Stream.eval(IO(x))
    def flatMap[A, B](fa: Stream[IO, A])(f: A => Stream[IO, B]): Stream[IO, B] = fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: A => Stream[IO, Either[A, B]]): Stream[IO, B] = f(a) flatMap {
      case Left(a2) => tailRecM(a2)(f)
      case Right(b) => pure(b)
    }
  }

  val interpreter: AMQP ~> Stream[IO, ?] = new BrokerStreamInterpreter[IO] or new BrokerEffectStreamInterpreter

  createConsumer.foldMap[Stream[IO, ?]](interpreter).run.unsafeRunSync()

}
