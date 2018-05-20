---
layout: docs
title:  "AckerConsumer"
number: 8
---

# AckerConsumer

It delegates the responsibility to acknowledge message consumption to the user. You are in total control of telling `RabbitMQ` when a message should be marked as consumed. It is the best option whenever you don't want to lose any messages.

```tut:book
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import fs2._

val queueName = QueueName("daQ")

def doSomething(consumer: StreamConsumer[IO], acker: StreamAcker[IO]): Stream[IO, Unit] = Stream.eval(IO.unit)

def program(implicit R: Fs2Rabbit[IO]) =
  R.createConnectionChannel.flatMap { implicit channel =>       // Stream[IO, AMQPChannel]
    for {
      ackerConsumer     <- R.createAckerConsumer(queueName)	    // (StreamAcker[IO], StreamConsumer[IO])
      (acker, consumer) = ackerConsumer
      _                 <- doSomething(consumer, acker)
    } yield ()
  }
```

When creating a consumer, you can tune the configuration by using `BasicQos` and `ConsumerArgs`. By default, the `basic QOS` is set to a prefetch size of 0, a prefetch count of 1 and global is set to false. The `ConsumerArgs` is by default None since it's optional. When defined, you can indicate `consumerTag` (default is ""), `noLocal` (default is false), `exclusive` (default is false) and `args` (default is an empty `Map[String, AnyRef]`).
