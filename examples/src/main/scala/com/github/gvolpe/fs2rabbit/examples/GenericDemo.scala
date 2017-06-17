package com.github.gvolpe.fs2rabbit.examples

import cats.effect.Effect
import com.github.gvolpe.fs2rabbit.Fs2Rabbit.{createAckerConsumer, createConnectionChannel, createPublisher, declareQueue}
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.{EffectScheduler, StreamLoop}
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder.jsonEncode
import com.github.gvolpe.fs2rabbit.model._
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class GenericDemo[F[_]](implicit F: Effect[F], ES: EffectScheduler[F]) {

  implicit val appS = scala.concurrent.ExecutionContext.Implicits.global
  implicit val appR = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")

  val queueName     = QueueName("testQ")
  val exchangeName  = ExchangeName("testEX")
  val routingKey    = RoutingKey("testRK")

  def logPipe: Pipe[F, AmqpEnvelope, AckResult] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- asyncF[F, Unit](println(s"Consumed: $amqpMsg"))
    } yield Ack(amqpMsg.deliveryTag)
  }

  val program = () => for {
    connAndChannel    <- createConnectionChannel[F]()
    (_, channel)      = connAndChannel
    _                 <- declareQueue[F](channel, queueName)
    (acker, consumer) = createAckerConsumer[F](channel, queueName)
    publisher         = createPublisher[F](channel, exchangeName, routingKey)
    result            <- new Flow(consumer, acker, logPipe, publisher).flow
  } yield result

  StreamLoop.run(program)

}

class Flow[F[_]](consumer: StreamConsumer[F],
                 acker: StreamAcker[F],
                 logger: Pipe[F, AmqpEnvelope, AckResult],
                 publisher: StreamPublisher[F])
                (implicit ec: ExecutionContext, F: Effect[F]) {

  import io.circe.generic.auto._

  case class Address(number: Int, streetName: String)
  case class Person(id: Long, name: String, address: Address)

  val simpleMessage = AmqpMessage("Hey!", AmqpProperties(None, None, Map("demoId" -> LongVal(123), "app" -> StringVal("fs2RabbitDemo"))))
  val classMessage  = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

  val flow: Stream[F, Unit] =
    Stream(
      Stream(simpleMessage).covary[F] to publisher,
      Stream(classMessage).covary[F]  through jsonEncode[F, Person] to publisher,
      consumer through logger to acker
    ).join(3)

}
