---
layout: docs
title:  "Queues"
number: 5
---

# Queues

Before getting into the `Consumers` section there are two things you need to know about `Queues`.

### Declaring a Queue

Declaring a `Queue` means that if already exists it's going to get a reference to it or otherwise it will create it.

```tut:book
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.config.declaration._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._
import fs2._

val q1 = QueueName("q1")
val q2 = QueueName("q2")

def exchanges(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel flatMap { implicit channel =>
  for {
    _ <- F.declareQueue(DeclarationQueueConfig.default(q1))
    _ <- F.declareQueue(DeclarationQueueConfig.default(q2))
  } yield ()
}
```

### Binding a Queue to an Exchange

```tut:book
val x1  = ExchangeName("x1")
val rk1 = RoutingKey("rk1")
val rk2 = RoutingKey("rk2")

def binding(F: Fs2Rabbit[IO])(implicit channel: AMQPChannel) =
  for {
    _ <- F.bindQueue(q1, x1, rk1)
    _ <- F.bindQueue(q2, x1, rk2)
  } yield ()
```
