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

import cats.effect.{IO, Ref}
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.model.*
import dev.profunktor.fs2rabbit.otel4s.instances.*
import fs2.Stream
import io.opentelemetry.api.trace.SpanKind as JavaSpanKind
import org.scalatest.flatspec.AnyFlatSpecLike
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes as Messaging
import org.typelevel.otel4s.trace.{SpanFinalizer, Tracer, TracerProvider}
import org.typelevel.otel4s.{Attribute, Attributes}

class RabbitConsumerCoverageSpec extends AnyFlatSpecLike with RabbitTracingTestSupport {

  private implicit val channel: AMQPChannel                 = dummyChannel
  private implicit val decoder: EnvelopeDecoder[IO, String] = AmqpEnvelope.stringDecoder[IO]

  it should "wrap every consumer constructor and preserve acknowledgement functions without settle spans" in
    withTestkit { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      val first                                       = envelope(queueDeliveryTag = 1L)

      for {
        acknowledged   <- Ref[IO].of(Vector.empty[(AckResult, AckMultiple)])
        tracer         <- RabbitTracer.create[IO](RabbitTracer.Config.default)
        client          = tracer.client(rabbitClient(new StubConsumingProgram(Stream.emit(first), acknowledged)))
        auto           <- client.createAutoAckConsumer[String](QueueName("auto"))
        manual         <- client.createAckerConsumer[String](
                            QueueName("manual"),
                            ackMultiple = AckMultiple(true)
                          )
        multiple       <- client.createAckerConsumerWithMultipleFlag[String](QueueName("multiple"))
        autoValues     <- auto.records.compile.toList
        manualValues   <- manual._2.records.compile.toList
        multipleValues <- multiple._2.records.compile.toList
        _              <- manual._1(AckResult.Ack(DeliveryTag(1L)))
        _              <- multiple._1(AckResult.NAck(DeliveryTag(2L)), AckMultiple(false))
        acks           <- acknowledged.get
        spans          <- testkit.finishedSpans
      } yield {
        autoValues shouldBe List(first)
        manualValues shouldBe List(first)
        multipleValues shouldBe List(first)
        auto.queueName shouldBe QueueName("auto")
        manual._2.queueName shouldBe QueueName("manual")
        multiple._2.queueName shouldBe QueueName("multiple")
        acks shouldBe Vector(
          AckResult.Ack(DeliveryTag(1L))  -> AckMultiple(true),
          AckResult.NAck(DeliveryTag(2L)) -> AckMultiple(false)
        )
        spans shouldBe empty
      }
    }

  it should "delegate records and create exactly one process span per recordsWithProcess element" in
    withTestkit { testkit =>
      for {
        moduleTracer <- testkit.tracerProvider.get("fs2.rabbit")
        consumer      = {
          implicit val tracer: Tracer[IO] = moduleTracer
          TracedRabbitConsumer[IO, String](
            QueueName("orders"),
            Stream(envelope(queueDeliveryTag = 1L), envelope(queueDeliveryTag = 2L)),
            RabbitTracer.Config.default
          )
        }
        raw          <- consumer.records.compile.toList
        processed    <- consumer.recordsWithProcess(value => IO.pure(value.deliveryTag.value)).compile.toList
        explicit     <- consumer.process(raw.head)(IO.pure("done"))
        spans        <- testkit.finishedSpans
      } yield {
        raw.map(_.deliveryTag.value) shouldBe List(1L, 2L)
        processed shouldBe List(1L, 2L)
        explicit shouldBe "done"
        spans.count(_.getName == "process orders:created:orders") shouldBe 3
        all(spans.map(_.getKind)) shouldBe JavaSpanKind.CONSUMER
        all(spans.map(_.getLinks.size)) shouldBe 0
      }
    }

  it should "link process spans with semantic link attributes and collapse an equal routing key and queue" in
    withTestkit { testkit =>
      for {
        creationTracer <- testkit.tracerProvider.get("creation")
        headers        <- {
          implicit val tracer: Tracer[IO] = creationTracer
          creationTracer.rootSpan("create-message").surround(Tracer[IO].propagate(Headers.empty))
        }
        moduleTracer   <- testkit.tracerProvider.get("fs2.rabbit")
        consumer        = {
          implicit val tracer: Tracer[IO] = moduleTracer
          TracedRabbitConsumer[IO, String](QueueName("created"), Stream.empty, RabbitTracer.Config.default)
        }
        _              <- consumer.process(envelope(headers))(IO.unit)
        spans          <- testkit.finishedSpans
      } yield {
        val span       = spanNamed(spans, "process orders:created")
        assertStringAttribute(span, Messaging.MessagingDestinationName("orders:created"))
        span.getLinks.size shouldBe 1
        val attributes = span.getLinks.get(0).getAttributes
        stringAttribute(attributes, Messaging.MessagingDestinationName.name) shouldBe Some("orders:created")
        stringAttribute(attributes, Messaging.MessagingRabbitmqDestinationRoutingKey.name) shouldBe Some("created")
        stringAttribute(attributes, Messaging.MessagingMessageId.name) shouldBe Some("message-1")
        longAttribute(attributes, Messaging.MessagingRabbitmqMessageDeliveryTag.name) shouldBe Some(7L)
      }
    }

  it should "mark generated queue destinations as anonymous" in
    withTestkit { testkit =>
      for {
        moduleTracer <- testkit.tracerProvider.get("fs2.rabbit")
        consumer      = {
          implicit val tracer: Tracer[IO] = moduleTracer
          TracedRabbitConsumer[IO, String](
            QueueName("amq.gen-random"),
            Stream.empty,
            RabbitTracer.Config.default
          )
        }
        _            <- consumer.process(envelope())(IO.unit)
        spans        <- testkit.finishedSpans
      } yield {
        val span = spanNamed(spans, "process orders:created:amq.gen-random")
        booleanAttribute(span.getAttributes, Messaging.MessagingDestinationAnonymous.name) shouldBe Some(true)
      }
    }

  it should "apply custom process span setup and finalization" in
    withTestkit { testkit =>
      val finalize: SpanFinalizer.Strategy = { case _ =>
        SpanFinalizer.addAttribute(Attribute("process.finalized", true))
      }
      val config                           = RabbitTracer.Config.default.withProcessSpanSetup(context =>
        RabbitTracer.Config.SpanSetup(
          s"handle ${context.queueName.value}",
          Attributes(Attribute("process.custom", context.redelivered)),
          finalize
        )
      )

      for {
        moduleTracer <- testkit.tracerProvider.get("fs2.rabbit")
        consumer      = {
          implicit val tracer: Tracer[IO] = moduleTracer
          TracedRabbitConsumer[IO, String](QueueName("orders"), Stream.empty, config)
        }
        redelivered   = envelope().copy(redelivered = true)
        _            <- consumer.process(redelivered)(IO.unit)
        spans        <- testkit.finishedSpans
      } yield {
        val span = spanNamed(spans, "handle orders")
        booleanAttribute(span.getAttributes, "process.custom") shouldBe Some(true)
        booleanAttribute(span.getAttributes, "process.finalized") shouldBe Some(true)
      }
    }

  it should "delegate consumer records and processing without spans when using the noop tracer" in
    withTestkit { testkit =>
      val first = envelope()

      for {
        acknowledged <- Ref[IO].of(Vector.empty[(AckResult, AckMultiple)])
        consumer     <- RabbitTracer
                          .noop[IO]
                          .client(rabbitClient(new StubConsumingProgram(Stream.emit(first), acknowledged)))
                          .createAutoAckConsumer[String](QueueName("orders"))
        values       <- consumer.recordsWithProcess(value => IO.pure(value.payload.reverse)).compile.toList
        spans        <- testkit.finishedSpans
      } yield {
        values shouldBe List("daolyap")
        spans shouldBe empty
      }
    }
}
