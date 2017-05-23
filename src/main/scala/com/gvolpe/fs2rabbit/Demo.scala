package com.gvolpe.fs2rabbit

import Fs2Utils._
import Fs2Rabbit._
import com.gvolpe.fs2rabbit.model._
import fs2.{Pipe, Stream, Task}

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

  val (consumer, acker) = createAckerConsumer(queueName)(libS)
  val publisher         = createPublisher("", queueName, routingKey)(libS)

  val program = fs2.concurrent.join(2)(
    Stream(
      Stream("Hey!") to publisher,
      consumer through logPipe to acker
    )
  )

  program.run.unsafeRun()

}
