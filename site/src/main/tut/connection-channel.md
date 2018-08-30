---
layout: docs
title:  "Connection and Channel"
number: 3
---

# Connection and Channel

These two are the primitive datatypes of the underlying `Java AMQP client`. What `Fs2Rabbit` provides is the guarantee of acquiring, using and releasing both `Connection` and `Channel` in a safe way by using `Stream.bracket`. Even in the case of failure it is guaranteed that all the resources will be cleaned up.

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import fs2._

def program(implicit R: Fs2Rabbit[IO]): Stream[IO, Unit] = {
  val connChannel = R.createConnectionChannel
  connChannel.flatMap { implicit channel =>
    // Here create consumers, publishers, etc
    Stream.eval(IO.unit)
  }
}
```
