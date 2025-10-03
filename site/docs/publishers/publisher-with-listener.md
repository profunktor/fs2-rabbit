---
layout: docs
title:  "Publisher with Listener"
number: 12
---

# Publisher with Listener

It is possible to add a listener when creating a publisher to handle messages that cannot be routed.

The `AMQP` protocol defines two different bits that can be set when publishing a message: `mandatory` and `immediate`. You can read more about it in the [AMQP reference](https://www.rabbitmq.com/amqp-0-9-1-reference.html). However, `RabbitMQ` only supports the `mandatory` bit in version 3.x so we don't support the `immediate` bit either.

#### Bit Mandatory

This flag tells the server how to react if the message cannot be routed to a queue. If this flag is set, the server will return an unroutable message with a Return method. If this flag is zero, the server silently drops the message.

The server SHOULD implement the mandatory flag.

### Creating a Publisher with Listener

It is simply created by specifying `ExchangeName`, `RoutingKey`, `PublishingFlag` and a listener, i.e. a function from `PublishReturn` to `F[Unit]`:

```scala mdoc:silent
import cats.effect._
import dev.profunktor.fs2rabbit.model.*
import dev.profunktor.fs2rabbit.interpreter.RabbitClient

val exchangeName = ExchangeName("testEX")
val routingKey   = RoutingKey("testRK")

val publishingFlag: PublishingFlag = PublishingFlag(mandatory = true)

val publishingListener: PublishReturn => IO[Unit] = pr => IO(println(s"Publish listener: $pr"))

def doSomething(publisher: String => IO[Unit]): IO[Unit] = IO.unit

def program(R: RabbitClient[IO]) =
  R.createConnectionChannel.use { implicit channel =>
    R.createPublisherWithListener[String](exchangeName, routingKey, publishingFlag, publishingListener).flatMap(doSomething)
  }
```

### Publishing a simple message

Once you have a `Publisher` you can start publishing messages by calling it:

```scala mdoc:silent
import cats.effect.Sync
import dev.profunktor.fs2rabbit.model.*

def publishSimpleMessage[F[_]: Sync](publisher: String => F[Unit]): F[Unit] = {
  val message = "Hello world!"
  publisher(message)
}
```

***NOTE: If the `mandatory` flag is set to `true` and there's no queue bound to the target exchange the message will return to the assigned publishing listener.***
