---
layout: docs
title:  "Client Metrics"
number: 18
---

# Client Metrics

RabbitMQ Java Client supports metrics collection via [Dropwizard](https://www.rabbitmq.com/blog/2016/11/30/metrics-support-in-rabbitmq-java-client-4-0/) or [Micrometer](https://www.rabbitmq.com/blog/2018/04/10/rabbitmq-java-client-metrics-with-micrometer-and-datadog/).
At the moment of writing both providers are in the `amqp-client` 5.9.0. You can instantiate one as shown below.

```scala
val registry            = new MetricRegistry
val dropwizardCollector = new StandardMetricsCollector(registry)
``` 

Now it is ready to use.

```scala
RabbitClient.default[IO](config).withMetricsCollector(dropwizardCollector).resource
```

## Expose via JMX

JMX provides a standard way to access performance metrics of an application. Dropwizard has a module to report metrics
via JMX with `metrics-jmx` module. Please add it to the list of the dependencies. 

```
libraryDependencies += "io.dropwizard.metrics" % "metrics-core" % "4.1.5"
libraryDependencies += "io.dropwizard.metrics" % "metrics-jmx"  % "4.1.5"
```

It provides `JmxReporter` for the metrics registry. It is a resource. It can be wrapped with acquire-release pattern for
ease to use. 

```scala
object JmxReporterResource {
  def make[F[_]: Sync](registry: MetricRegistry): Resource[F, JmxReporter] = {
    val acquire = Sync[F].delay {
      val reporter = JmxReporter.forRegistry(registry).inDomain("com.rabbitmq.client.jmx").build
      reporter.start()
      reporter
    }

    val close = (reporter: JmxReporter) => Sync[F].delay(reporter.close()).void

    Resource.make(acquire)(close)
  }
}
```

Let's initialise the FS2 RabbitMQ client and AMQP channel with metrics.

```scala
val resources = for {
  _       <- JmxReporterResource.make[IO](registry)
  client  <- RabbitClient.default[IO](config).withMetricsCollector(dropwizardCollector).resource
  channel <- client.createConnection.flatMap(client.createChannel)
} yield (channel, client)

val program = resources.use {
  case (channel, client) =>
    // Let's publish and consume and see the counters go up
}
```

The app is going to have now the following metrics under `com.rabbitmq.client.jmx`:
* Acknowledged messages
* Channels count
* Connections count
* Consumed messages
* Published messages
* Rejected messages

## Full Listing

Let's create an application that publishes and consumes messages with exposed JMX metrics on top of the Cats Effect.

```scala mdoc:silent
import java.nio.charset.StandardCharsets.UTF_8

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{ExitCode, IO, IOApp, Resource, Sync}
import cats.implicits._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
import com.rabbitmq.client.impl.StandardMetricsCollector
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model.ExchangeType.Topic
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.examples.putStrLn
import fs2._

import scala.concurrent.duration._

object DropwizardMetricsDemo extends IOApp {

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    nodes = NonEmptyList.one(Fs2RabbitNodeConfig(host = "127.0.0.1", port = 5672)),
    username = Some("guest"),
    password = Some("guest"),
    ssl = false,
    connectionTimeout = 3.seconds,
    requeueOnNack = false,
    requeueOnReject = false,
    internalQueueSize = Some(500),
    requestedHeartbeat = 60.seconds,
    automaticRecovery = true,
    clientProvidedConnectionName = Some("app:rabbit")
  )

  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")

  val simpleMessage = AmqpMessage("Hey!", AmqpProperties.empty)

  implicit val stringMessageEncoder =
    Kleisli[IO, AmqpMessage[String], AmqpMessage[Array[Byte]]] { s =>
      s.copy(payload = s.payload.getBytes(UTF_8)).pure[IO]
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val registry            = new MetricRegistry
    val dropwizardCollector = new StandardMetricsCollector(registry)

    val resources = for {
      _       <- JmxReporterResource.make[IO](registry)
      client  <- RabbitClient.default[IO](config).withMetricsCollector(dropwizardCollector).resource
      channel <- client.createConnection.flatMap(client.createChannel)
    } yield (channel, client)

    val program = resources.use {
      case (channel, client) =>
        implicit val c = channel

        val setup =
          for {
            _                 <- client.declareQueue(DeclarationQueueConfig.default(queueName))
            _                 <- client.declareExchange(DeclarationExchangeConfig.default(exchangeName, Topic))
            _                 <- client.bindQueue(queueName, exchangeName, routingKey)
            ackerConsumer     <- client.createAckerConsumer[String](queueName)
            (acker, consumer) = ackerConsumer
            publisher         <- client.createPublisher[AmqpMessage[String]](exchangeName, routingKey)
          } yield (consumer, acker, publisher)

        Stream
          .eval(setup)
          .flatTap {
            case (consumer, acker, publisher) =>
              Stream(
                Stream(simpleMessage).evalMap(publisher).repeat.metered(1.second),
                consumer.through(logPipe).evalMap(acker)
              ).parJoin(2)
          }
          .compile
          .drain
    }

    program.as(ExitCode.Success)
  }

  def logPipe[F[_]: Sync]: Pipe[F, AmqpEnvelope[String], AckResult] =
    _.evalMap { amqpMsg =>
      putStrLn(s"Consumed: $amqpMsg").as(Ack(amqpMsg.deliveryTag))
    }

}

object JmxReporterResource {
  def make[F[_]: Sync](registry: MetricRegistry): Resource[F, JmxReporter] = {
    val acquire = Sync[F].delay {
      val reporter = JmxReporter.forRegistry(registry).inDomain("com.rabbitmq.client.jmx").build
      reporter.start()
      reporter
    }

    val close = (reporter: JmxReporter) => Sync[F].delay(reporter.close()).void

    Resource.make(acquire)(close)
  }
}
```
