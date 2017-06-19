package com.github.gvolpe.fs2rabbit.free

import cats.effect.{Effect, IO}
import cats.free._
import cats.{Monad, ~>}
import com.github.gvolpe.fs2rabbit.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._
import fs2.Stream

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object FreeExample extends App {

  val queueName     = QueueName("testQ")
  val exchangeName  = ExchangeName("testEX")
  val routingKey    = RoutingKey("testRK")

  val createFreeConsumer: Free[AMQPBroker, AmqpEnvelope] = for {
    connAndChannel <- Free.liftF(CreateConnectionAndChannel)
    (_, channel)   = connAndChannel
    _              <- Free.liftF(DeclareQueue(channel, queueName))
    _              <- Free.liftF(DeclareExchange(channel, exchangeName, ExchangeType.Topic))
    _              <- Free.liftF(BindQueue(channel, queueName, exchangeName, routingKey))
    consumer       <- Free.liftF(CreateAutoAckConsumer(channel, queueName))
  } yield consumer

  def streamInterpreter[F[_]: Effect](implicit ec: ExecutionContext): AMQPBroker ~> Stream[F, ?] =
    new (AMQPBroker ~> Stream[F, ?]) {
      override def apply[A](fa: AMQPBroker[A]): Stream[F, A] = fa match {
        case CreateConnectionAndChannel               => Fs2Rabbit.createConnectionChannel[F]()
        case CreateAutoAckConsumer(channel, queue)    => Fs2Rabbit.createAutoAckConsumer[F](channel, queue)
        case DeclareExchange(channel, exName, exType) => Fs2Rabbit.declareExchange[F](channel, exName, exType)
        case DeclareQueue(channel, queue)             => Fs2Rabbit.declareQueue[F](channel, queue)
        case BindQueue(channel, queue, exName, rk)    => Fs2Rabbit.bindQueue[F](channel, queue, exName, rk)
      }
    }

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  implicit val streamMonad = new Monad[Stream[IO, ?]] {
    def pure[A](x: A): Stream[IO, A] = Stream.eval(IO(x))
    def flatMap[A, B](fa: Stream[IO, A])(f: A => Stream[IO, B]): Stream[IO, B] = fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: A => Stream[IO, Either[A, B]]): Stream[IO, B] = f(a) flatMap {
      case Left(a2) => tailRecM(a2)(f)
      case Right(b) => pure(b)
    }
  }

  println(Free.foldMap[AMQPBroker, Stream[IO, ?]](streamInterpreter[IO])) //.run.unsafeRunSync()

}
