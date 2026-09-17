/*
 * Copyright 2017-2026 ProfunKtor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.profunktor.fs2rabbit.otel4s

import cats.data.Kleisli
import cats.effect.unsafe.implicits.global
import cats.effect.{Deferred, IO, Ref}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{IntVal, StringVal}
import dev.profunktor.fs2rabbit.model.*
import dev.profunktor.fs2rabbit.otel4s.instances.*
import io.opentelemetry.api.trace.{SpanKind as JavaSpanKind, StatusCode as JavaStatusCode}
import io.opentelemetry.api.trace.{Span, SpanContext, TraceFlags, TraceState}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapGetter as JavaTextMapGetter, TextMapPropagator, TextMapSetter}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.typelevel.otel4s.context.propagation.TextMapGetter
import org.typelevel.otel4s.oteljava.testkit.OtelJavaTestkit
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes as Messaging
import org.typelevel.otel4s.trace.{SpanFinalizer, TracerProvider}
import org.typelevel.otel4s.{Attribute, Attributes}

import java.util.Collections

class RabbitPublisherCoverageSpec extends AnyFlatSpecLike with RabbitTracingTestSupport {

  private implicit val channel: AMQPChannel                                  = dummyChannel
  private implicit val encoder: MessageEncoder[IO, AmqpMessage[Array[Byte]]] = Kleisli(IO.pure)

  it should "trace every publisher constructor and forward flags and listeners" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      val flag                                        = PublishingFlag(mandatory = true)
      val listener: PublishReturn => IO[Unit]         = _ => IO.unit

      for {
        published   <- Ref[IO].of(Vector.empty[Published])
        listeners   <- Ref[IO].of(Vector.empty[PublishReturn => IO[Unit]])
        tracer      <- RabbitTracer.create[IO](RabbitTracer.Config.default)
        client       = tracer.client(rabbitClient(new StubPublishingProgram(published, listeners)))
        direct      <- client.createPublisher[AmqpMessage[Array[Byte]]](ExchangeName("direct"), RoutingKey("one"))
        directFlag  <- client.createPublisherWithListener[AmqpMessage[Array[Byte]]](
                         ExchangeName("direct"),
                         RoutingKey("two"),
                         flag,
                         listener
                       )
        routing     <- client.createRoutingPublisher[AmqpMessage[Array[Byte]]](ExchangeName("routing"))
        routingFlag <- client.createRoutingPublisherWithListener[AmqpMessage[Array[Byte]]](
                         ExchangeName("routing"),
                         flag,
                         listener
                       )
        basic       <- client.createBasicPublisher[AmqpMessage[Array[Byte]]]
        basicFlag   <- client.createBasicPublisherWithListener[AmqpMessage[Array[Byte]]](flag, listener)
        _           <- direct(message())
        _           <- directFlag(message())
        _           <- routing(RoutingKey("three"))(message())
        _           <- routingFlag(RoutingKey("four"))(message())
        _           <- basic(ExchangeName("basic"), RoutingKey("five"), message())
        _           <- basicFlag(ExchangeName("basic"), RoutingKey("six"), message())
        sent        <- published.get
        registered  <- listeners.get
        spans       <- testkit.finishedSpans
      } yield {
        sent.map(value => (value.exchangeName.value, value.routingKey.value, value.flag)) shouldBe Vector(
          ("direct", "one", None),
          ("direct", "two", Some(flag)),
          ("routing", "three", None),
          ("routing", "four", Some(flag)),
          ("basic", "five", None),
          ("basic", "six", Some(flag))
        )
        registered.size shouldBe 3
        spans.count(_.getKind == JavaSpanKind.PRODUCER) shouldBe 6
      }
    }

  it should "record publishing failures" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider

      for {
        published <- Ref[IO].of(Vector.empty[Published])
        listeners <- Ref[IO].of(Vector.empty[PublishReturn => IO[Unit]])
        tracer    <- RabbitTracer.create[IO](RabbitTracer.Config.default)
        publisher <- tracer
                       .client(
                         rabbitClient(
                           new StubPublishingProgram(
                             published,
                             listeners,
                             _ => IO.raiseError(new IllegalStateException("publish failed"))
                           )
                         )
                       )
                       .createPublisher[AmqpMessage[Array[Byte]]](ExchangeName("orders"), RoutingKey("failed"))
        result    <- publisher(message()).attempt
        spans     <- testkit.finishedSpans
      } yield {
        result.left.map(_.getMessage) shouldBe Left("publish failed")
        val span = spanNamed(spans, "publish orders:failed")
        span.getStatus.getStatusCode shouldBe JavaStatusCode.ERROR
        stringAttribute(span.getAttributes, "error.type") shouldBe Some(classOf[IllegalStateException].getCanonicalName)
      }
    }

  it should "finalize a canceled publish span" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider

      for {
        started   <- Deferred[IO, Unit]
        published <- Ref[IO].of(Vector.empty[Published])
        listeners <- Ref[IO].of(Vector.empty[PublishReturn => IO[Unit]])
        tracer    <- RabbitTracer.create[IO](RabbitTracer.Config.default)
        publisher <- tracer
                       .client(
                         rabbitClient(
                           new StubPublishingProgram(published, listeners, _ => started.complete(()).void *> IO.never)
                         )
                       )
                       .createPublisher[AmqpMessage[Array[Byte]]](ExchangeName("orders"), RoutingKey("blocked"))
        fiber     <- publisher(message()).start
        _         <- started.get
        _         <- fiber.cancel
        spans     <- testkit.finishedSpans
      } yield {
        val span = spanNamed(spans, "publish orders:blocked")
        span.getStatus.getStatusCode shouldBe JavaStatusCode.ERROR
        stringAttribute(span.getAttributes, "error.type") shouldBe Some("canceled")
      }
    }

  it should "apply custom span setup and attribute precedence without implicit endpoint metadata" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      val finalize: SpanFinalizer.Strategy            = { case _ =>
        SpanFinalizer.addAttribute(Attribute("custom.finalized", true))
      }
      val config                                      = RabbitTracer.Config.default
        .withConstAttributes(
          Attributes(
            Messaging.MessagingDestinationName("constant-destination"),
            Attribute("attribute.priority", "constant")
          )
        )
        .withPublishSpanSetup(_ =>
          RabbitTracer.Config.SpanSetup(
            "custom publish",
            Attributes(Attribute("attribute.priority", "span")),
            finalize
          )
        )

      for {
        published <- Ref[IO].of(Vector.empty[Published])
        listeners <- Ref[IO].of(Vector.empty[PublishReturn => IO[Unit]])
        tracer    <- RabbitTracer.create[IO](config)
        publisher <- tracer
                       .client(rabbitClient(new StubPublishingProgram(published, listeners)))
                       .createPublisher[AmqpMessage[Array[Byte]]](ExchangeName("orders"), RoutingKey("created"))
        _         <- publisher(message())
        spans     <- testkit.finishedSpans
      } yield {
        val span = spanNamed(spans, "custom publish")
        stringAttribute(span.getAttributes, Messaging.MessagingDestinationName.name) shouldBe Some(
          "constant-destination"
        )
        stringAttribute(span.getAttributes, "attribute.priority") shouldBe Some("span")
        booleanAttribute(span.getAttributes, "custom.finalized") shouldBe Some(true)
        stringAttribute(span.getAttributes, "server.address") shouldBe None
        longAttribute(span.getAttributes, "server.port") shouldBe None
      }
    }

  it should "replace malformed propagation input and use the default-exchange destination" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      val malformed                                   = Headers(
        "traceparent" -> StringVal("malformed"),
        "application" -> IntVal(42)
      )

      for {
        published <- Ref[IO].of(Vector.empty[Published])
        listeners <- Ref[IO].of(Vector.empty[PublishReturn => IO[Unit]])
        tracer    <- RabbitTracer.create[IO](RabbitTracer.Config.default)
        publisher <- tracer
                       .client(rabbitClient(new StubPublishingProgram(published, listeners)))
                       .createPublisher[AmqpMessage[Array[Byte]]](ExchangeName(""), RoutingKey(""))
        _         <- publisher(message(malformed))
        sent      <- published.get.map(_.head)
        spans     <- testkit.finishedSpans
      } yield {
        TextMapGetter[Headers].get(sent.message.properties.headers, "traceparent") should not be Some("malformed")
        sent.message.properties.headers.getOpt("application") shouldBe Some(IntVal(42))
        val span = spanNamed(spans, "publish amq.default")
        span.getKind shouldBe JavaSpanKind.PRODUCER
        assertStringAttribute(span, Messaging.MessagingDestinationName("amq.default"))
      }
    }

  it should "honor a configured non-W3C propagator and expose publish link attributes" in {
    val customPropagator = new FixedContextPropagator

    OtelJavaTestkit
      .inMemory[IO](_.addTextMapPropagators(customPropagator))
      .use { testkit =>
        implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
        val headers                                     = Headers("x-rabbit-context" -> StringVal("recognized"))

        for {
          published <- Ref[IO].of(Vector.empty[Published])
          listeners <- Ref[IO].of(Vector.empty[PublishReturn => IO[Unit]])
          tracer    <- RabbitTracer.create[IO](RabbitTracer.Config.default)
          publisher <- tracer
                         .client(rabbitClient(new StubPublishingProgram(published, listeners)))
                         .createPublisher[AmqpMessage[Array[Byte]]](ExchangeName("orders"), RoutingKey("created"))
          _         <- publisher(message(headers))
          sent      <- published.get.map(_.head)
          spans     <- testkit.finishedSpans
        } yield {
          sent.message.properties.headers shouldBe headers
          val span       = spanNamed(spans, "publish orders:created")
          span.getKind shouldBe JavaSpanKind.CLIENT
          span.getLinks.size shouldBe 1
          val attributes = span.getLinks.get(0).getAttributes
          stringAttribute(attributes, Messaging.MessagingDestinationName.name) shouldBe Some("orders:created")
          stringAttribute(attributes, Messaging.MessagingRabbitmqDestinationRoutingKey.name) shouldBe Some("created")
          stringAttribute(attributes, Messaging.MessagingMessageId.name) shouldBe Some("message-1")
        }
      }
      .unsafeRunSync()
  }

  private final class FixedContextPropagator extends TextMapPropagator {
    private val remoteContext = SpanContext.createFromRemoteParent(
      "11111111111111111111111111111111",
      "2222222222222222",
      TraceFlags.getSampled,
      TraceState.getDefault
    )

    override def fields(): java.util.Collection[String] = Collections.singletonList("x-rabbit-context")

    override def inject[C](context: Context, carrier: C, setter: TextMapSetter[C]): Unit = ()

    override def extract[C](context: Context, carrier: C, getter: JavaTextMapGetter[C]): Context =
      Option(getter.get(carrier, "x-rabbit-context")) match {
        case Some("recognized") => context.`with`(Span.wrap(remoteContext))
        case _                  => context
      }
  }
}
