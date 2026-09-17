---
layout: docs
title: "OpenTelemetry tracing"
number: 7
---

# OpenTelemetry tracing

The `fs2-rabbit-otel4s` module adds otel4s spans and trace-context propagation to
publishers and consumers without requiring the OpenTelemetry Java agent.

```scala
libraryDependencies += "dev.profunktor" %% "fs2-rabbit-otel4s" % Version
```

Create a `RabbitTracer` from the application's otel4s `TracerProvider`, then use
its traced client for publishers and consumers. The tracer should normally be
created once alongside the `RabbitClient`:

```scala
import cats.effect.Async
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*
import dev.profunktor.fs2rabbit.otel4s.*
import org.typelevel.otel4s.trace.TracerProvider

def program[F[_]: Async: TracerProvider](client: RabbitClient[F])(implicit
    channel: AMQPChannel
): F[Unit] =
  RabbitTracer
    .resource[F](
      RabbitTracer.Config.default
        .withServerAddress("rabbitmq.example.com", Some(5672))
        .withClientId("orders-service")
    )
    .use { rabbitTracer =>
      val traced = rabbitTracer.client(client)

      for {
        _ <- publish(traced)
        _ <- consume(traced)
      } yield ()
    }
```

The encoder and decoder instances are the same ones used by the regular
`RabbitClient` API. All publisher variants and the auto-ack and manual-ack consumer
constructors have traced equivalents.

## Publishing

Create publishers from the `TracedRabbitClient` in the same way as from a regular
client. Each invocation of the returned function creates a publish span and
injects a creation context into the AMQP headers when the message does not
already carry one:

```scala
def publish[F[_]: Async](client: TracedRabbitClient[F])(implicit
    channel: AMQPChannel
): F[Unit] =
  for {
    publish <- client.createPublisher[String](
                 ExchangeName("orders"),
                 RoutingKey("created")
               )
    _ <- publish("order-123")
  } yield ()
```

The routing publisher, basic publisher, publishing flags, and returned-message
listener variants are also available on `TracedRabbitClient`.

## Consuming

`recordsWithProcess` places a process span around the effect returned for each
message:

```scala
def consume[F[_]: Async](client: TracedRabbitClient[F])(implicit
    channel: AMQPChannel
): F[Unit] =
  for {
    consumer <- client.createAutoAckConsumer[String](QueueName("order-workers"))
    _ <- consumer
           .recordsWithProcess { envelope =>
             Async[F].delay(println(envelope.payload))
           }
           .compile
           .drain
  } yield ()
```

For manual acknowledgement, use `createAckerConsumer` or
`createAckerConsumerWithMultipleFlag`. Acknowledgement and rejection use the
regular fs2-rabbit functions returned by those constructors and do not create
additional spans.

Use `records` and `consumer.process(envelope)(effect)` when the processing
boundary needs to be placed manually:

```scala
consumer.records.evalMap { envelope =>
  consumer.process(envelope)(handle(envelope))
}
```

## Syntax

Importing `dev.profunktor.fs2rabbit.otel4s.syntax.*` enables two convenience
extensions. They do not change tracing behavior; they are shorter forms of the
regular API:

```scala
import dev.profunktor.fs2rabbit.otel4s.syntax.*

val traced = client.traced(rabbitTracer)
// equivalent to rabbitTracer.client(client)
```

`processTraced` is useful with a manually chosen processing boundary. It requires
the corresponding `TracedRabbitConsumer` to be in implicit scope:

```scala
implicit val tracedConsumer: TracedRabbitConsumer[F, String] = consumer

consumer.records.evalMap { envelope =>
  envelope.processTraced(handle(envelope))
  // equivalent to consumer.process(envelope)(handle(envelope))
}
```

`recordsWithProcess` is usually simpler when the whole `evalMap` operation should
be traced.

## Span model

| Operation | Span name | Kind | Parent and links |
| --- | --- | --- | --- |
| Publish a message without creation context | `publish <destination>` | `PRODUCER` | The ambient span is the parent. A new creation context is injected into the AMQP headers. |
| Publish a message with creation context | `publish <destination>` | `CLIENT` | The ambient span is the parent. The message creation context is preserved and linked. |
| Process a delivered message | `process <destination>` | `CONSUMER` | Always a root span. The message creation context is linked when present. |

A message published without an existing creation context gets a `PRODUCER` span,
and that span's context is injected into the message headers. If the message
already contains valid trace context, the headers are preserved and publishing
gets a `CLIENT` span linked to that context.

Processing creates one root `CONSUMER` span per delivered message and links it to
the creation context in the message headers. RabbitMQ pushes messages to consumers,
so the module emits `process` spans rather than `receive` spans.

The destination is a colon-separated combination of exchange, routing key, and,
for processing, queue name, with empty components removed. The default exchange
and an empty routing key are represented as `amq.default`.

## Semantic attributes

Every span has `messaging.system=rabbitmq`, `messaging.operation.name`,
`messaging.operation.type`, and `messaging.destination.name`. When applicable it
also has:

- `messaging.rabbitmq.destination.routing_key`
- `messaging.message.id` and `messaging.message.conversation_id`
- `messaging.rabbitmq.message.delivery_tag` on process spans
- `messaging.destination.anonymous=true` for generated queue names
- configured `messaging.client.id`, `server.address`, and `server.port`

Links repeat the destination, routing key, message id, and, for processing, the
delivery tag. Body and envelope size attributes are omitted because the public
fs2-rabbit APIs do not expose a reliable encoded envelope size at every
instrumented boundary.

## Customization

The defaults follow the current OpenTelemetry RabbitMQ messaging semantic
conventions. Span names, extra attributes, and finalization behavior can be
customized with `RabbitTracer.Config.withPublishSpanSetup` and
`withProcessSpanSetup`. Use `RabbitTracer.noop` when tracing is disabled.

Do not enable overlapping RabbitMQ Java-agent instrumentation for the same client;
doing so can create duplicate messaging spans.
