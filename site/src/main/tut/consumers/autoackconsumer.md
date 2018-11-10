---
layout: docs
title:  "AutoAckConsumer"
number: 7
---

# AutoAckConsumer

It acknowledges message consumption automatically so all you need to worry about is to process the message. Every message consumed will be marked as consumed in `RabbitMQ` meaning that in case of error messages could be lost.

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import fs2._

val queueName = QueueName("daQ")

def doSomething(consumer: StreamConsumer[IO, String]): Stream[IO, Unit] = Stream.eval(IO.unit)

def program(implicit R: Fs2Rabbit[IO]) =
  R.createConnectionChannel.flatMap { implicit channel => // Stream[IO, AMQPChannel]
    for {
      c <- R.createAutoAckConsumer[String](queueName)	    // StreamConsumer[IO, String]
      _ <- doSomething(c)
    } yield ()
  }
```

When creating a consumer, you can tune the configuration by using `BasicQos` and `ConsumerArgs`. By default, the `basic QOS` is set to a prefetch size of 0, a prefetch count of 1 and global is set to false. The `ConsumerArgs` is by default None since it's optional. When defined, you can indicate `consumerTag` (default is ""), `noLocal` (default is false), `exclusive` (default is false) and `args` (default is an empty `Map[String, ?]`).
