---
layout: docs
title:  "Publisher"
number: 11
---

# Publisher

A `Publisher` is simply created by specifying an `ExchangeName` and a `RoutingKey`:

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit

val exchangeName = ExchangeName("testEX")
val routingKey   = RoutingKey("testRK")

def doSomething(publisher: String => IO[Unit]): IO[Unit] = IO.unit

def program(implicit R: Fs2Rabbit[IO]) =
  R.createConnectionChannel use { implicit channel =>
    for {
      p <- R.createPublisher[String](exchangeName, routingKey)	  // String => IO[Unit]
      _ <- doSomething(p)
    } yield ()
  }
```

### Publishing a simple message

Once you have a `Publisher` you can start publishing messages by simpy calling it:

```tut:book:silent
import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.model._

def publishSimpleMessage[F[_]: Sync](publisher: String => F[Unit]): F[Unit] = {
  val message = "Hello world!"
  publisher(message)
}
```
