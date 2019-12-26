---
layout: docs
title:  "Publisher"
number: 11
---

# Publisher

A `Publisher` is simply created by specifying an `ExchangeName` and a `RoutingKey`:

```scala mdoc:silent
import cats.effect._
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.interpreter.RabbitClient

val exchangeName = ExchangeName("testEX")
val routingKey   = RoutingKey("testRK")

def doSomething(publisher: String => IO[Unit]): IO[Unit] = IO.unit

def program(R: RabbitClient[IO]) =
  R.createConnectionChannel.use { implicit channel =>
    R.createPublisher[String](exchangeName, routingKey).flatMap(doSomething)
  }
```

### Publishing a simple message

Once you have a `Publisher` you can start publishing messages by simpy calling it:

```scala mdoc:silent
import cats.effect.Sync
import dev.profunktor.fs2rabbit.model._

def publishSimpleMessage[F[_]: Sync](publisher: String => F[Unit]): F[Unit] =
  publisher("Hello world!")
```
