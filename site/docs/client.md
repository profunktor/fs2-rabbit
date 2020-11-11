---
layout: docs
title:  "Fs2 Rabbit Client"
number: 2
---

# Fs2 Rabbit Client

`RabbitClient` is the main client that wraps the communication  with `RabbitMQ`. The mandatory arguments are a `Fs2RabbitConfig` and a `cats.effect.Dispatcher` for running effects under the hood. Optionally, you can pass in a custom `SSLContext` and `SaslConfig`.
An alternative constructor is provided for creating a `cats.effect.Resource[F, Rabbit[F]]` without directly handling the `Dispatcher`.

```scala mdoc:silent
import cats.effect._
import cats.effect.std.Dispatcher
import com.rabbitmq.client.{DefaultSaslConfig, SaslConfig}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import javax.net.ssl.SSLContext

object RabbitClient {
  def apply[F[_]: Async](
    config: Fs2RabbitConfig,
    dispatcher: Dispatcher[F],
    sslContext: Option[SSLContext] = None,
    saslConfig: SaslConfig = DefaultSaslConfig.PLAIN
  ): F[RabbitClient[F]] = ???

  def resource[F[_]: Async](
    config: Fs2RabbitConfig,
    sslContext: Option[SSLContext] = None,
    saslConfig: SaslConfig = DefaultSaslConfig.PLAIN
  ): Resource[F, RabbitClient[F]] = ???
}
```

Its creation is effectful so you need to `flatMap` and pass it as an argument. For example:

```scala mdoc:silent
import cats.effect._
import cats.effect.std.Dispatcher
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import java.util.concurrent.Executors

object Program {
  def foo[F[_]](client: RabbitClient[F]): F[Unit] = ???
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
    requeueOnReject = false,
    internalQueueSize = Some(500)
  )

  override def run(args: List[String]): IO[ExitCode] =
    Dispatcher[IO].use { dispatcher =>
      RabbitClient[IO](config, dispatcher).flatMap { client =>
        Program.foo[IO](client).as(ExitCode.Success)
      }
    }

}
```
