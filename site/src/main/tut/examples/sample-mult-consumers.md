---
layout: docs
title:  "Multiple Consumers"
number: 16
---

# Multiple Consumers

Given two `Consumers` bound to queues with different `RoutingKey`s `RKA` and `RKB` and a single `Publisher` bound to a single `RoutingKey` named `RKA` we will be publishing messages to both queues but expecting to only consume messages published to the `RKA`. The second consumer bound to `RKB` will not receive any messages:

```tut:book:silent
import cats.effect._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.model._
import fs2._

import scala.concurrent.ExecutionContext

implicit val cs = IO.contextShift(ExecutionContext.global)

val q1  = QueueName("q1")
val q2  = QueueName("q2")
val ex  = ExchangeName("testEX")
val rka = RoutingKey("RKA")
val rkb = RoutingKey("RKB")

val msg = Stream("Hey!").covary[IO]

def multipleConsumers(c1: Stream[IO, AmqpEnvelope[String]], c2: Stream[IO, AmqpEnvelope[String]], p: String => IO[Unit]) = {
  Stream(
    msg evalMap p,
    c1.through(_.evalMap(m => IO(println(s"Consumer #1 >> $m")))),
    c2.through(_.evalMap(m => IO(println(s"Consumer #2 >> $m"))))
  ).parJoin(3)
}

def program(R: Fs2Rabbit[IO]) =
  R.createConnectionChannel.use { implicit channel =>
    for {
      _  <- R.declareExchange(ex, ExchangeType.Topic)
      _  <- R.declareQueue(DeclarationQueueConfig.default(q1))
      _  <- R.declareQueue(DeclarationQueueConfig.default(q2))
      _  <- R.bindQueue(q1, ex, rka)
      _  <- R.bindQueue(q2, ex, rkb)
      c1 <- R.createAutoAckConsumer[String](q1)
      c2 <- R.createAutoAckConsumer[String](q2)
      p  <- R.createPublisher[String](ex, rka)
      _  <- multipleConsumers(c1, c2, p).compile.drain
    } yield ()
  }
```

If we run this program, we should only see a message `Consumer #1 >> Hey!` meaning that only the consumer bound to the `RKA` routing key got the message.
