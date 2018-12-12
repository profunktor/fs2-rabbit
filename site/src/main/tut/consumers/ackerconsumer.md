---
layout: docs
title:  "AckerConsumer"
number: 8
---

# AckerConsumer

An `AckerConsumer` delegates the responsibility to acknowledge messages to the user. You are in total control of telling `RabbitMQ` when and if a message should be marked as consumed. Use this if you can't lose any messages.

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit

val queueName = QueueName("daQ")

def doSomething(consumer: IO[AmqpEnvelope[String]], acker: AckResult => IO[Unit]): IO[Unit] = IO.unit

def program(implicit R: Fs2Rabbit[IO]) =
  R.createConnectionChannel use { implicit channel =>
    for {
      (acker, consumer) <- R.createAckerConsumer[String](queueName)	    // (AckResult => IO[Unit], IO[AmqpEnvelope[String]])
      _                 <- doSomething(consumer, acker)
    } yield ()
  }
```

When creating a consumer, you can tune the configuration by using `BasicQos` and `ConsumerArgs`. By default, the `basic QOS` is set to a prefetch size of 0, a prefetch count of 1 and `global` is set to false. `ConsumerArgs` is by `None` by default since it's optional. When defined, you can indicate `consumerTag` (default is ""), `noLocal` (default is false), `exclusive` (default is false) and `args` (default is an empty `Map[String, ?]`).
