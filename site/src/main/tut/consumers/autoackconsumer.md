---
layout: docs
title:  "AutoAckConsumer"
number: 7
---

# AutoAckConsumer

An `AutoAckConsumer` acknowledges every consumed message automatically, so all you need to worry about is to process the message. Keep in mind that messages whose processing fails will still be acknowledged to `RabbitMQ` meaning that messages could get lost.

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit

val queueName = QueueName("daQ")

def doSomething(consumer: IO[AmqpEnvelope[String]]): IO[Unit] = IO.unit

def program(implicit R: Fs2Rabbit[IO]) =
  R.createConnectionChannel use { implicit channel =>
    for {
      c <- R.createAutoAckConsumer[String](queueName)	    // IO[AmqpEnvelope[String]]
      _ <- doSomething(c)
    } yield ()
  }
```

When creating a consumer, you can tune the configuration by using `BasicQos` and `ConsumerArgs`. By default, the `basicQOS` is set to a prefetch size of 0, a prefetch count of 1 and `global` is set to false. The `ConsumerArgs` is `None` by default since it's optional. When defined, you can indicate `consumerTag` (default is ""), `noLocal` (default is false), `exclusive` (default is false) and `args` (default is an empty `Map[String, ?]`).
