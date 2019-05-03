---
layout: docs
title:  "Queues"
number: 5
---

# Queues

Before getting into the `Consumers` section there are two things you need to know about `Queue`s.

### Declaring a Queue

Declaring a `Queue` will either create a new one or, in case a queue of that name was already declared, returns a reference to an existing one.

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.config.declaration._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._

val q1 = QueueName("q1")
val q2 = QueueName("q2")

def exchanges(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel use { implicit channel =>
  for {
    _ <- F.declareQueue(DeclarationQueueConfig.default(q1))
    _ <- F.declareQueue(DeclarationQueueConfig.default(q2))
  } yield ()
}
```

### Binding a Queue to an Exchange

```tut:book:silent
val x1  = ExchangeName("x1")
val rk1 = RoutingKey("rk1")
val rk2 = RoutingKey("rk2")

def binding(F: Fs2Rabbit[IO])(implicit channel: AMQPChannel) =
  for {
    _ <- F.bindQueue(q1, x1, rk1)
    _ <- F.bindQueue(q2, x1, rk2)
  } yield ()
```
