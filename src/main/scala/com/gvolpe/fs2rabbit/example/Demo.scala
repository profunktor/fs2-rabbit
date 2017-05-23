package com.gvolpe.fs2rabbit.example

import com.gvolpe.fs2rabbit.Fs2Rabbit.{createAckerConsumer, createPublisher}
import com.gvolpe.fs2rabbit.Fs2Utils.async
import com.gvolpe.fs2rabbit.model._
import fs2.{Pipe, Sink, Strategy, Stream, Task}

object Demo extends App {

  implicit val appS = fs2.Strategy.fromFixedDaemonPool(4, "fs2-rabbit-demo")

  // For creation of connection, consumer and publisher
  val libS          = fs2.Strategy.fromFixedDaemonPool(4, "fs2-rabbit")

  val queueName: QueueName    = "test"
  val routingKey: RoutingKey  = "test"

  def logPipe: Pipe[Task, AmqpMessage, AckResult] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- async(println(s"Consumed: ${amqpMsg.payload}"))
    } yield Ack(amqpMsg.deliveryTag)
  }

  val program = for {
    _                 <- Stream.eval(fs2.async.boundedQueue[Task, String](100))
    (consumer, acker) = createAckerConsumer(queueName)(libS)
    publisher         = createPublisher("", queueName, routingKey)(libS)
    result            <- new Flow(consumer, acker, logPipe, publisher).flow
  } yield result

  program.run.unsafeRun()

}

class Flow(consumer: Stream[Task, AmqpMessage],
           acker: Sink[Task, AckResult],
           logger: Pipe[Task, AmqpMessage, AckResult],
           publisher: Sink[Task, String])
          (implicit S: Strategy) {

  val flow: Stream[Task, Unit] = fs2.concurrent.join(2)(
    Stream(
      Stream("Hey!") to publisher,
      consumer through logger to acker
    )
  )

}