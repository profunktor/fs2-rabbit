package com.github.gvolpe.fs2rabbit.example

import com.github.gvolpe.fs2rabbit.Fs2Rabbit._
import com.github.gvolpe.fs2rabbit.Fs2Utils._
import com.github.gvolpe.fs2rabbit.model._
import fs2.{Pipe, Strategy, Stream, Task}

object Demo extends App {

  implicit val appS = fs2.Strategy.fromFixedDaemonPool(4, "fs2-rabbit-demo")
  implicit val appR = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")

  // For creation of consumer and publisher
  val libS          = fs2.Strategy.fromFixedDaemonPool(4, "fs2-rabbit")

  val queueName: QueueName    = "test"
  val routingKey: RoutingKey  = "test"

  def logPipe: Pipe[Task, AmqpEnvelope, AckResult] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- async(println(s"Consumed: ${amqpMsg.payload}"))
    } yield Ack(amqpMsg.deliveryTag)
  }

  val program = () => for {
    connAndChannel    <- createConnectionChannel()
    (_, channel)      = connAndChannel
    _                 <- declareQueue(channel, queueName)
    (acker, consumer) = createAckerConsumer(channel, queueName)(libS)
    publisher         = createPublisher(channel, "", routingKey)(libS)
    result            <- new Flow(consumer, acker, logPipe, publisher).flow
  } yield result

  Loop.run(program)

}

class Flow(consumer: StreamConsumer,
           acker: StreamAcker,
           logger: Pipe[Task, AmqpEnvelope, AckResult],
           publisher: StreamPublisher)
          (implicit S: Strategy) {

  val flow: Stream[Task, Unit] = fs2.concurrent.join(2)(
    Stream(
      Stream("Hey!") to publisher,
      consumer through logger to acker
    )
  )

}