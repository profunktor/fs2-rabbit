---
layout: docs
title:  "Queues"
number: 5
---

# Queues

Before getting into the `Consumers` section there are two things you need to know about `Queue`s.

### Declaring a Queue

Declaring a `Queue` will either create a new one or, in case a queue of that name was already declared, returns a reference to an existing one.

```scala mdoc:silent
import cats.effect.IO
import cats.implicits.*
import dev.profunktor.fs2rabbit.config.declaration.*
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*

val q1 = QueueName("q1")
val q2 = QueueName("q2")

def exchanges(R: RabbitClient[IO]) =
  R.createConnectionChannel.use { implicit channel =>
    R.declareQueue(DeclarationQueueConfig.default(q1)) *>
    R.declareQueue(DeclarationQueueConfig.default(q2))
  }
```

### Binding a Queue to an Exchange

```scala mdoc:silent
val x1  = ExchangeName("x1")
val rk1 = RoutingKey("rk1")
val rk2 = RoutingKey("rk2")

def binding(R: RabbitClient[IO])(implicit channel: AMQPChannel) =
  R.bindQueue(q1, x1, rk1) *> R.bindQueue(q2, x1, rk2)
```
