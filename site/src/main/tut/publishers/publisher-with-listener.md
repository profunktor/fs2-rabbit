---
layout: docs
title:  "Publisher with Listener"
number: 12
---

# Publisher with Listener

It is possible to add a `PublishingListener` when creating a `Publisher` to handle the messages that can not be routed.

The `AMQP` protocol defines two different bits that can be set when publishing a message: `mandatory` and `immediate`. You can read more about it in the [AMQP reference](https://www.rabbitmq.com/amqp-0-9-1-reference.html). However, `RabbitMQ` only supports the `mandatory` bit in version 3.x so we don't support the `immediate` bit either.

#### Bit Mandatory

This flag tells the server how to react if the message cannot be routed to a queue. If this flag is set, the server will return an unroutable message with a Return method. If this flag is zero, the server silently drops the message.

The server SHOULD implement the mandatory flag.

### Creating a Publisher with Listener

It is simply created by specifying `ExchangeName`, `RoutingKey`, `PublishingFlag` and a `PublishingListener`. The latter is just a function from `PublishReturn` (a data structure) to `F[Unit]`:

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import fs2._

val exchangeName = ExchangeName("testEX")
val routingKey   = RoutingKey("testRK")

val publishingFlag: PublishingFlag = PublishingFlag(mandatory = true)

// Run when there's no consumer for the routing key specified by the publisher and the flag mandatory is true
val publishingListener: PublishingListener[IO] = pr => IO(println(s"Publish listener: $pr"))

def doSomething(publisher: Publisher[IO]): Stream[IO, Unit] = Stream.eval(IO.unit)

def program(implicit R: Fs2Rabbit[IO]) =
  R.createConnectionChannel.flatMap { implicit channel => // Stream[IO, AMQPChannel]
    for {
      p <- R.createPublisherWithListener(exchangeName, routingKey, publishingFlag, publishingListener)	  // Publisher[IO]
      _ <- doSomething(p)
    } yield ()
  }
```

### Publishing a simple message

Once you have a `Publisher` you can start publishing messages by connecting a source `Stream[F, AmqpMessage]`:

```tut:book:silent
import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.model._
import fs2._

def publishSimpleMessage[F[_]: Sync](publisher: Publisher[F]): Stream[F, Unit] = {
  val message = AmqpMessage("Hello world!", AmqpProperties.empty)
  Stream(message).covary[F] evalMap publisher
}
```

***NOTE: If the `mandatory` flag is set to `true` and there's no queue bounded to this exchange the message will return to the assigned publishing listener.***
