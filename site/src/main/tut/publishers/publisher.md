---
layout: docs
title:  "Publisher"
number: 11
---

# Publisher

A `Publisher` is simply created by specifying an `ExchangeName` and a `RoutingKey`. In addition, you need to pass in a `cats.effect.Blocker` since publishing are blocking actions in the underlying Java client:

```tut:book:silent
import cats.effect._
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import java.util.concurrent.Executors

val exchangeName = ExchangeName("testEX")
val routingKey   = RoutingKey("testRK")

def doSomething(publisher: String => IO[Unit]): IO[Unit] = IO.unit

def resources(client: Fs2Rabbit[IO]): Resource[IO, (AMQPChannel, Blocker)] =
  for {
    channel    <- client.createConnectionChannel
    blockingES = Resource.make(IO(Executors.newCachedThreadPool()))(es => IO(es.shutdown()))
    blocker    <- blockingES.map(Blocker.liftExecutorService)
  } yield (channel, blocker)

def program(client: Fs2Rabbit[IO]) =
  resources(client).use {
    case (channel, blocker) =>
      implicit val rabbitChannel = channel
      client.createPublisher[String](exchangeName, routingKey, blocker).flatMap(doSomething)
  }
```

### Publishing a simple message

Once you have a `Publisher` you can start publishing messages by simpy calling it:

```tut:book:silent
import cats.effect.Sync
import dev.profunktor.fs2rabbit.model._

def publishSimpleMessage[F[_]: Sync](publisher: String => F[Unit]): F[Unit] =
  publisher("Hello world!")
```
