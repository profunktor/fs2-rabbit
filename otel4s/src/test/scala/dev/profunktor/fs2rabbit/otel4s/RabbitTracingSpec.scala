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
import cats.effect.{IO, Ref}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*
import dev.profunktor.fs2rabbit.otel4s.instances.*
import dev.profunktor.fs2rabbit.program.PublishingProgram
import fs2.Stream
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.{SpanKind as JavaSpanKind, StatusCode as JavaStatusCode}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.sdk.trace.data.SpanData
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.context.propagation.TextMapGetter
import org.typelevel.otel4s.oteljava.testkit.OtelJavaTestkit
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes as Messaging
import org.typelevel.otel4s.trace.{Tracer, TracerProvider}

class RabbitTracingSpec extends AnyFlatSpecLike with Matchers {

  private val dummyChannel: AMQPChannel = RabbitChannel(null)

  it should "create a producer publish span and inject its context" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO]                    = testkit.tracerProvider
      implicit val channel: AMQPChannel                                  = dummyChannel
      implicit val encoder: MessageEncoder[IO, AmqpMessage[Array[Byte]]] = Kleisli(IO.pure)

      for {
        captured  <- Ref[IO].of(Option.empty[AmqpMessage[Array[Byte]]])
        client     = recordingClient(captured)
        tracer    <- RabbitTracer.create[IO](
                       RabbitTracer.Config.default
                         .withServerAddress("rabbitmq.example.com", Some(5672))
                         .withConstAttributes(Attributes(Attribute("config.attribute", "retained")))
                         .withClientId("orders-service")
                     )
        publisher <- tracer
                       .client(client)
                       .createPublisher[AmqpMessage[Array[Byte]]](
                         ExchangeName("orders"),
                         RoutingKey("created")
                       )
        appTracer <- testkit.tracerProvider.get("application")
        _         <- appTracer.rootSpan("ambient").surround(publisher(message()))
        published <- captured.get.map(_.getOrElse(fail("message was not published")))
        spans     <- testkit.finishedSpans
      } yield {
        TextMapGetter[Headers].get(published.properties.headers, "traceparent") should not be empty
        val span = spanNamed(spans, "publish orders:created")
        span.getKind shouldBe JavaSpanKind.PRODUCER
        span.getParentSpanContext.isValid shouldBe true
        span.getLinks shouldBe empty
        assertStringAttribute(span, Messaging.MessagingSystem(Messaging.MessagingSystemValue.Rabbitmq))
        assertStringAttribute(span, Messaging.MessagingOperationName("publish"))
        assertStringAttribute(span, Messaging.MessagingOperationType(Messaging.MessagingOperationTypeValue.Send))
        assertStringAttribute(span, Messaging.MessagingDestinationName("orders:created"))
        assertStringAttribute(span, Messaging.MessagingRabbitmqDestinationRoutingKey("created"))
        assertStringAttribute(span, Messaging.MessagingMessageId("message-1"))
        assertStringAttribute(span, Messaging.MessagingMessageConversationId("conversation-1"))
        assertStringAttribute(span, Messaging.MessagingClientId("orders-service"))
        attribute(span, "config.attribute") shouldBe "retained"
        attribute(span, "server.address") shouldBe "rabbitmq.example.com"
        longAttribute(span, "server.port") shouldBe 5672L
      }
    }

  it should "preserve existing creation context and create a linked client publish span" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO]                    = testkit.tracerProvider
      implicit val channel: AMQPChannel                                  = dummyChannel
      implicit val encoder: MessageEncoder[IO, AmqpMessage[Array[Byte]]] = Kleisli(IO.pure)

      for {
        appTracer          <- testkit.tracerProvider.get("application")
        headers            <- {
          implicit val tracer: Tracer[IO] = appTracer
          appTracer.rootSpan("create-message").surround(Tracer[IO].propagate(Headers.empty))
        }
        originalTraceparent = TextMapGetter[Headers]
                                .get(headers, "traceparent")
                                .getOrElse(fail("missing source trace context"))
        captured           <- Ref[IO].of(Option.empty[AmqpMessage[Array[Byte]]])
        tracer             <- RabbitTracer.create[IO](RabbitTracer.Config.default)
        publisher          <- tracer
                                .client(recordingClient(captured))
                                .createPublisher[AmqpMessage[Array[Byte]]](
                                  ExchangeName("orders"),
                                  RoutingKey("created")
                                )
        _                  <- publisher(message(headers))
        published          <- captured.get.map(_.getOrElse(fail("message was not published")))
        spans              <- testkit.finishedSpans
      } yield {
        TextMapGetter[Headers].get(published.properties.headers, "traceparent") shouldBe Some(originalTraceparent)
        val span = spanNamed(spans, "publish orders:created")
        span.getKind shouldBe JavaSpanKind.CLIENT
        span.getLinks.size shouldBe 1
      }
    }

  it should "create a root consumer process span linked to message creation context" in
    withTestkit { testkit =>
      for {
        appTracer    <- testkit.tracerProvider.get("application")
        headers      <- {
          implicit val tracer: Tracer[IO] = appTracer
          appTracer.rootSpan("create-message").surround(Tracer[IO].propagate(Headers.empty))
        }
        moduleTracer <- testkit.tracerProvider.get("fs2.rabbit")
        consumer      = {
          implicit val tracer: Tracer[IO] = moduleTracer
          val config                      = RabbitTracer.Config.default
            .withServerAddress("rabbitmq.example.com", Some(5672))
            .withConstAttributes(Attributes(Attribute("config.attribute", "retained")))
          TracedRabbitConsumer[IO, String](
            QueueName("orders-queue"),
            Stream.empty,
            config
          )
        }
        envelope      = AmqpEnvelope(
                          DeliveryTag(7L),
                          "payload",
                          AmqpProperties.empty.copy(messageId = Some("message-1"), headers = headers),
                          ExchangeName("orders"),
                          RoutingKey("created"),
                          redelivered = false
                        )
        _            <- appTracer.rootSpan("ambient").surround(consumer.process(envelope)(IO.unit))
        spans        <- testkit.finishedSpans
      } yield {
        val span = spanNamed(spans, "process orders:created:orders-queue")
        span.getKind shouldBe JavaSpanKind.CONSUMER
        span.getParentSpanContext.isValid shouldBe false
        span.getLinks.size shouldBe 1
        assertStringAttribute(span, Messaging.MessagingOperationType(Messaging.MessagingOperationTypeValue.Process))
        assertLongAttribute(span, Messaging.MessagingRabbitmqMessageDeliveryTag(7L))
        assertStringAttribute(span, Messaging.MessagingMessageId("message-1"))
        attribute(span, "config.attribute") shouldBe "retained"
        attribute(span, "server.address") shouldBe "rabbitmq.example.com"
        longAttribute(span, "server.port") shouldBe 5672L
      }
    }

  it should "record processing failures on the process span" in
    withTestkit { testkit =>
      for {
        moduleTracer <- testkit.tracerProvider.get("fs2.rabbit")
        consumer      = {
          implicit val tracer: Tracer[IO] = moduleTracer
          TracedRabbitConsumer[IO, String](
            QueueName("orders-queue"),
            Stream.empty,
            RabbitTracer.Config.default
          )
        }
        envelope      = AmqpEnvelope(
                          DeliveryTag(8L),
                          "payload",
                          AmqpProperties.empty,
                          ExchangeName("orders"),
                          RoutingKey("created"),
                          redelivered = false
                        )
        _            <- consumer.process(envelope)(IO.raiseError(new IllegalStateException("boom"))).attempt
        spans        <- testkit.finishedSpans
      } yield {
        val span = spanNamed(spans, "process orders:created:orders-queue")
        span.getStatus.getStatusCode shouldBe JavaStatusCode.ERROR
        attribute(span, "error.type") shouldBe classOf[IllegalStateException].getCanonicalName
      }
    }

  it should "delegate without spans or propagation when using the noop tracer" in
    withTestkit { testkit =>
      implicit val channel: AMQPChannel                                  = dummyChannel
      implicit val encoder: MessageEncoder[IO, AmqpMessage[Array[Byte]]] = Kleisli(IO.pure)

      for {
        captured  <- Ref[IO].of(Option.empty[AmqpMessage[Array[Byte]]])
        publisher <- RabbitTracer
                       .noop[IO]
                       .client(recordingClient(captured))
                       .createPublisher[AmqpMessage[Array[Byte]]](
                         ExchangeName("orders"),
                         RoutingKey("created")
                       )
        _         <- publisher(message())
        published <- captured.get.map(_.getOrElse(fail("message was not published")))
        spans     <- testkit.finishedSpans
      } yield {
        TextMapGetter[Headers].get(published.properties.headers, "traceparent") shouldBe None
        spans shouldBe empty
      }
    }

  private def withTestkit[A](run: OtelJavaTestkit[IO] => IO[A]): A =
    OtelJavaTestkit
      .inMemory[IO](_.addTextMapPropagators(W3CTraceContextPropagator.getInstance()))
      .use(run)
      .unsafeRunSync()

  private def message(headers: Headers = Headers.empty): AmqpMessage[Array[Byte]] =
    AmqpMessage(
      "body".getBytes("UTF-8"),
      AmqpProperties.empty.copy(
        messageId = Some("message-1"),
        correlationId = Some("conversation-1"),
        headers = headers
      )
    )

  private def recordingClient(captured: Ref[IO, Option[AmqpMessage[Array[Byte]]]]): RabbitClient[IO] =
    new RabbitClient[IO](
      null,
      null,
      null,
      null,
      null,
      new RecordingPublishingProgram(captured)
    )

  private def spanNamed(spans: List[SpanData], name: String): SpanData =
    spans.find(_.getName == name).getOrElse(fail(s"missing span [$name], found ${spans.map(_.getName)}"))

  private def attribute(span: SpanData, name: String): String =
    span.getAttributes.get(AttributeKey.stringKey(name))

  private def longAttribute(span: SpanData, name: String): Long =
    span.getAttributes.get(AttributeKey.longKey(name)).longValue()

  private def assertStringAttribute(span: SpanData, expected: Attribute[String]): Unit =
    attribute(span, expected.key.name) shouldBe expected.value

  private def assertLongAttribute(span: SpanData, expected: Attribute[Long]): Unit =
    longAttribute(span, expected.key.name) shouldBe expected.value

  private final class RecordingPublishingProgram(captured: Ref[IO, Option[AmqpMessage[Array[Byte]]]])
      extends PublishingProgram[IO] {
    override def basicPublish(
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        message: AmqpMessage[Array[Byte]]
    ): IO[Unit] = captured.set(Some(message))

    override def basicPublishWithFlag(
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flag: PublishingFlag,
        message: AmqpMessage[Array[Byte]]
    ): IO[Unit] = captured.set(Some(message))

    override def addPublishingListener(channel: AMQPChannel, listener: PublishReturn => IO[Unit]): IO[Unit] = IO.unit
    override def clearPublishingListeners(channel: AMQPChannel): IO[Unit]                                   = IO.unit

    override def createPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
        encoder: MessageEncoder[IO, A]
    ): IO[A => IO[Unit]] = IO.pure(value => encoder.run(value).flatMap(message => captured.set(Some(message))))

    override def createPublisherWithListener[A](
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flags: PublishingFlag,
        listener: PublishReturn => IO[Unit]
    )(implicit encoder: MessageEncoder[IO, A]): IO[A => IO[Unit]] = unsupported

    override def createRoutingPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName)(implicit
        encoder: MessageEncoder[IO, A]
    ): IO[RoutingKey => A => IO[Unit]] = unsupported

    override def createRoutingPublisherWithListener[A](
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        flags: PublishingFlag,
        listener: PublishReturn => IO[Unit]
    )(implicit encoder: MessageEncoder[IO, A]): IO[RoutingKey => A => IO[Unit]] = unsupported

    override def createBasicPublisher[A](channel: AMQPChannel)(implicit
        encoder: MessageEncoder[IO, A]
    ): IO[(ExchangeName, RoutingKey, A) => IO[Unit]] = unsupported

    override def createBasicPublisherWithListener[A](
        channel: AMQPChannel,
        flags: PublishingFlag,
        listener: PublishReturn => IO[Unit]
    )(implicit encoder: MessageEncoder[IO, A]): IO[(ExchangeName, RoutingKey, A) => IO[Unit]] = unsupported

    private def unsupported[A]: IO[A] = IO.raiseError(new UnsupportedOperationException("unused in test"))
  }
}
