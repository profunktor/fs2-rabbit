---
layout: docs
title:  "Connection and Channel"
number: 3
---

# Connection and Channel

These two are the primitive datatypes of the underlying `Java AMQP client`. What `RabbitClient` provides is the guarantee of acquiring, using and releasing both `Connection` and `Channel` in a safe way by using `Resource`. Even in the case of failure it is guaranteed that all the resources will be cleaned up.

```tut:book:silent
import cats.effect.{IO, Resource}
import cats.implicits._
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{AMQPChannel, AMQPConnection}

def program(R: RabbitClient[IO]): IO[Unit] = {
  val connChannel: Resource[IO, AMQPChannel] = R.createConnectionChannel
  connChannel.use { implicit channel =>
    // Here create consumers, publishers, etc
    IO.unit
  }
}
```

# Multiple channels per connection

Creating a `Connection` is expensive so you might want to reuse it and create multiple `Channel`s from it. There are two primitive operations that allow you to do this:

- `createConnection: Resource[F, AMQPConnection]`
- `createChannel(conn: AMQPConnection): Resource[F, AMQPChannel]`

The operation `createConnectionChannel` is a convenient function defined in terms of these two primitives.

```tut:book:silent
def foo(R: RabbitClient[IO])(implicit channel: AMQPChannel): IO[Unit] = IO.unit

def multChannels(R: RabbitClient[IO]): IO[Unit] =
  R.createConnection.use { conn =>
    R.createChannel(conn).use { implicit channel =>
      foo(R)
    } *>
    R.createChannel(conn).use { implicit channel =>
      foo(R)
    }
  }
```
