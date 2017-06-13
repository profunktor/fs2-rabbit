package com.github.gvolpe.fs2rabbit.example

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.Fs2Rabbit._
import com.github.gvolpe.fs2rabbit.Fs2Utils._
import com.github.gvolpe.fs2rabbit.StreamLoop
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder._
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContext

object Demo extends App {

  implicit val appS = scala.concurrent.ExecutionContext.Implicits.global
  implicit val appR = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")

  val queueName: QueueName    = "test"
  val routingKey: RoutingKey  = "test"

  def logPipe: Pipe[IO, AmqpEnvelope, AckResult] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- asyncF[IO, Unit](println(s"Consumed: $amqpMsg"))
    } yield Ack(amqpMsg.deliveryTag)
  }

  val program = () => for {
    connAndChannel    <- createConnectionChannel[IO]()
    (_, channel)      = connAndChannel
    _                 <- declareQueue[IO](channel, queueName)
    (acker, consumer) = createAckerConsumer[IO](channel, queueName)
    publisher         = createPublisher[IO](channel, "", routingKey)
    result            <- new Flow(consumer, acker, logPipe, publisher).flow
  } yield result

  StreamLoop.run(program)

}

class Flow(consumer: StreamConsumer[IO],
           acker: StreamAcker[IO],
           logger: Pipe[IO, AmqpEnvelope, AckResult],
           publisher: StreamPublisher[IO])
          (implicit ec: ExecutionContext) {

  import io.circe.generic.auto._

  case class Address(number: Int, streetName: String)
  case class Person(id: Long, name: String, address: Address)

  val simpleMessage = AmqpMessage("Hey!", AmqpProperties(None, None, Map("demoId" -> LongVal(123), "app" -> StringVal("fs2RabbitDemo"))))
  val classMessage  = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

  val flow: Stream[IO, Unit] =
    Stream(
      Stream(simpleMessage).covary[IO] to publisher,
      Stream(classMessage).covary[IO]  through jsonEncode[IO, Person] to publisher,
      consumer through logger to acker
    ).join(3)

}