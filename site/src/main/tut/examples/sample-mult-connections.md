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

```tut:book:silent
import cats.effect.IO
import com.itv.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.itv.fs2rabbit.interpreter.Fs2Rabbit
import com.itv.fs2rabbit.model._
import fs2._

import scala.concurrent.ExecutionContext.Implicits.global

val q1 = QueueName("q1")
val ex = ExchangeName("testEX")
val rk = RoutingKey("RKA")
```

Here's our program `p1` creating a `Consumer` representing the first `Connection`:

```tut:book
def p1(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel.flatMap { implicit channel =>
  for {
    _  <- F.declareExchange(ex, ExchangeType.Topic)
    _  <- F.declareQueue(DeclarationQueueConfig.default(q1))
    _  <- F.bindQueue(q1, ex, rk)
    c1 <- F.createAutoAckConsumer(q1)
  } yield c1
}
```

Here's our program `p2` creating a `Consumer` representing the second `Connection`:

```tut:book
def p2(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel.flatMap { implicit channel =>
  for {
    _  <- F.declareExchange(ex, ExchangeType.Topic)
    _  <- F.declareQueue(DeclarationQueueConfig.default(q1))
    _  <- F.bindQueue(q1, ex, rk)
    c2 <- F.createAutoAckConsumer(q1)
  } yield c2
}
```

Here's our program `p3` creating a `Publisher` representing the third `Connection`:

```tut:book
def p3(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel.flatMap { implicit channel =>
  for {
    _  <- F.declareExchange(ex, ExchangeType.Topic)
    pb <- F.createPublisher(ex, rk)
  } yield pb
}
```

And finally we compose all the three programs together:

```tut:book
val pipe: Pipe[IO, AmqpEnvelope, AmqpMessage[String]] = _.map(env => AmqpMessage(env.payload, AmqpProperties.empty))

def program(implicit F: Fs2Rabbit[IO]) =
  for {
    c1 <- p1
    c2 <- p2
    pb <- p3
    _  <- (c1 through pipe to pb).concurrently(c2 through pipe to pb)
  } yield ()
```
