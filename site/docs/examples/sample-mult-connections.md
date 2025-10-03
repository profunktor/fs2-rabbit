---
layout: docs
title:  "Multiple Connections"
number: 17
---

# Multiple Connections

This advanced case presents the challenge of having multiple (3) `RabbitMQ Connections` interacting with each other.

We start by defining three different programs representing each connection, namely `p1`, `p2` and `p3` respectively.

- `p1` defines a single `Consumer` for `Connection` 1, namely `c1`.
- `p2` defines a single `Consumer` for `Connection` 2, namely `c2`.
- `p3` defines a single `Publisher` for `Connection` 3.

We will be consuming messages from `c1` and `c2`, and publishing the result to `p3` concurrently. Thanks to `fs2` this becomes such a simple case:

```scala mdoc:silent
import cats.effect.*
import cats.implicits.*
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*
import fs2.*

val q1 = QueueName("q1")
val ex = ExchangeName("testEX")
val rk = RoutingKey("RKA")
```

Here's our program `p1` creating a `Consumer` representing the first `Connection`:

```scala mdoc:silent
def p1(R: RabbitClient[IO]) =
  R.createConnectionChannel.use { implicit channel =>
    R.declareExchange(ex, ExchangeType.Topic) *>
    R.declareQueue(DeclarationQueueConfig.default(q1)) *>
    R.bindQueue(q1, ex, rk) *>
    R.createAutoAckConsumer[String](q1)
  }
```

Here's our program `p2` creating a `Consumer` representing the second `Connection`:

```scala mdoc:silent
def p2(R: RabbitClient[IO]) =
  R.createConnectionChannel use { implicit channel =>
    R.declareExchange(ex, ExchangeType.Topic) *>
    R.declareQueue(DeclarationQueueConfig.default(q1)) *>
    R.bindQueue(q1, ex, rk) *>
    R.createAutoAckConsumer[String](q1)
  }
```

Here's our program `p3` creating a `Publisher` representing the third `Connection`:

```scala mdoc:silent
def p3(R: RabbitClient[IO]) =
  R.createConnectionChannel use { implicit channel =>
    R.declareExchange(ex, ExchangeType.Topic) *>
    R.createPublisher(ex, rk)
  }
```

And finally we compose all the three programs together:

```scala mdoc:silent
val pipe: Pipe[IO, AmqpEnvelope[String], String] = _.map(_.payload)

def program(c: RabbitClient[IO]) =
  (p1(c), p2(c), p3(c)).mapN { case (c1, c2, pb) =>
    (c1.through(pipe).evalMap(pb)).concurrently(c2.through(pipe).evalMap(pb)).compile.drain
  }
```
