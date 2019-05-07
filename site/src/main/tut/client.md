---
layout: docs
title:  "Fs2 Rabbit Client"
number: 2
---

# Fs2 Rabbit Client

`Fs2Rabbit` is the main client that wraps the communication  with `RabbitMQ`. All it needs are a `Fs2RabbitConfig`, an optional `SSLContext` and an instance of `ConcurrentEffect[F]`.

```tut:book:silent
import cats.effect._
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import javax.net.ssl.SSLContext

object Fs2Rabbit {
  def apply[F[_]: ConcurrentEffect](
    config: Fs2RabbitConfig,
    sslContext: Option[SSLContext] = None
  ): F[Fs2Rabbit[F]] = ???
}
```

Its creation is effectful so you need to `flatMap` and pass it as an argument. For example:

```tut:book:silent
import cats.effect.{ExitCode, IOApp}
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit

object Program {
  def foo[F[_]](client: Fs2Rabbit[F]): F[Unit] = ???
}

class Demo extends IOApp {

  val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    host = "127.0.0.1",
    username = Some("guest"),
    password = Some("guest"),
    port = 5672,
    ssl = false,
    connectionTimeout = 3,
    requeueOnNack = false,
    internalQueueSize = Some(500)
  )

  override def run(args: List[String]): IO[ExitCode] =
    Fs2Rabbit[IO](config).flatMap { client =>
      Program.foo[IO](client).as(ExitCode.Success)
    }

}
```

