---
layout: docs
title:  "Fs2 Rabbit Client"
number: 2
---

# Fs2 Rabbit Client

`RabbitClient` is the main client that wraps the communication  with `RabbitMQ`. The mandatory arguments are a `Fs2RabbitConfig` and a `cats.effect.Blocker` used for publishing (this action is blocking in the underlying Java client). Optionally, you can pass in a custom `SSLContext` and `SaslConfig`.

```scala mdoc:silent
import cats.effect._
import com.rabbitmq.client.{DefaultSaslConfig, SaslConfig}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import javax.net.ssl.SSLContext

object RabbitClient {
  def apply[F[_]: ConcurrentEffect: ContextShift](
    config: Fs2RabbitConfig,
    blocker: Blocker,
    sslContext: Option[SSLContext] = None,
    saslConfig: SaslConfig = DefaultSaslConfig.PLAIN
  ): F[RabbitClient[F]] = ???
}
```

Its creation is effectful so you need to `flatMap` and pass it as an argument. For example:

```scala mdoc:silent
import cats.effect._
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.model._
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
    internalQueueSize = Some(500)
  )

  val blockerResource =
    Resource
      .make(IO(Executors.newCachedThreadPool()))(es => IO(es.shutdown()))
      .map(Blocker.liftExecutorService)

  override def run(args: List[String]): IO[ExitCode] =
    blockerResource.use { blocker =>
      RabbitClient[IO](config, blocker).flatMap { client =>
        Program.foo[IO](client).as(ExitCode.Success)
      }
    }

}
```
