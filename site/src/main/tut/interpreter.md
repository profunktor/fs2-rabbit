---
layout: docs
title:  "Fs2 Rabbit Interpreter"
number: 2
---

# Fs2 Rabbit Interpreter

It is the main interpreter that will be interacting with `RabbitMQ`, a.k.a. the client. All it needs are a `Fs2RabbitConfig`, an optional `SSLContext` and an implicit instance of `ConcurrentEffect[F]`. Its creation is effectful so it is wrapped in `F`.

```tut:book:silent
import cats.effect._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import javax.net.ssl.SSLContext

object Fs2Rabbit {
  def apply[F[_]: ConcurrentEffect](
    config: Fs2RabbitConfig,
    sslContext: Option[SSLContext] = None
  ): F[Fs2Rabbit[F]] = ???
}
```

The recommended way to create the interpreter is to call `apply` and `flatMap` on it to make it available as an implicit. For example:

```tut:book:silent
import cats.effect.{ExitCode, IOApp}
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import fs2._

object Program {
  def foo[F[_]](implicit R: Fs2Rabbit[F]): Stream[F, Unit] = ???
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
    requeueOnNack = false
  )

  override def run(args: List[String]): IO[ExitCode] =
    Fs2Rabbit[IO](config).flatMap { implicit fs2Rabbit =>
      Program.foo[IO].compile.drain.as(ExitCode.Success)
    }

}
```

