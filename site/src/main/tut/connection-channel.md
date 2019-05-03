---
layout: docs
title:  "Connection and Channel"
number: 3
---

# Connection and Channel

These two are the primitive datatypes of the underlying `Java AMQP client`. What `Fs2Rabbit` provides is the guarantee of acquiring, using and releasing both `Connection` and `Channel` in a safe way by using `Resource`. Even in the case of failure it is guaranteed that all the resources will be cleaned up.

```tut:book:silent
import cats.effect.{IO, Resource}
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model.AMQPChannel

def program(implicit R: Fs2Rabbit[IO]): IO[Unit] = {
  val connChannel: Resource[IO, AMQPChannel] = R.createConnectionChannel
  connChannel use { implicit channel =>
    // Here create consumers, publishers, etc
    IO.unit
  }
}
```
