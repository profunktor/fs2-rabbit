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

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*
import dev.profunktor.fs2rabbit.program.{AckConsumingProgram, PublishingProgram}
import fs2.Stream
import io.opentelemetry.api.common.{AttributeKey as JavaAttributeKey, Attributes as JavaAttributes}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.sdk.trace.data.SpanData
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.oteljava.testkit.OtelJavaTestkit

private[otel4s] final case class Published(
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    flag: Option[PublishingFlag],
    message: AmqpMessage[Array[Byte]]
)

trait RabbitTracingTestSupport extends Matchers {

  protected val dummyChannel: AMQPChannel = RabbitChannel(null)

  protected def withTestkit[A](run: OtelJavaTestkit[IO] => IO[A]): A =
    OtelJavaTestkit
      .inMemory[IO](_.addTextMapPropagators(W3CTraceContextPropagator.getInstance()))
      .use(run)
      .unsafeRunSync()

  protected def message(headers: Headers = Headers.empty): AmqpMessage[Array[Byte]] =
    AmqpMessage(
      "body".getBytes("UTF-8"),
      AmqpProperties.empty.copy(
        messageId = Some("message-1"),
        correlationId = Some("conversation-1"),
        headers = headers
      )
    )

  protected def envelope(
      headers: Headers = Headers.empty,
      queueDeliveryTag: Long = 7L,
      exchangeName: ExchangeName = ExchangeName("orders"),
      routingKey: RoutingKey = RoutingKey("created")
  ): AmqpEnvelope[String] =
    AmqpEnvelope(
      DeliveryTag(queueDeliveryTag),
      "payload",
      AmqpProperties.empty.copy(
        messageId = Some("message-1"),
        correlationId = Some("conversation-1"),
        headers = headers
      ),
      exchangeName,
      routingKey,
      redelivered = false
    )

  protected def rabbitClient(
      publishingProgram: PublishingProgram[IO],
      consumingProgram: AckConsumingProgram[IO] = null
  ): RabbitClient[IO] =
    new RabbitClient[IO](null, null, null, null, consumingProgram, publishingProgram)

  protected def rabbitClient(consumingProgram: AckConsumingProgram[IO]): RabbitClient[IO] =
    new RabbitClient[IO](null, null, null, null, consumingProgram, null)

  protected def spanNamed(spans: List[SpanData], name: String): SpanData =
    spans.find(_.getName == name).getOrElse(fail(s"missing span [$name], found ${spans.map(_.getName)}"))

  protected def stringAttribute(attributes: JavaAttributes, name: String): Option[String] =
    Option(attributes.get(JavaAttributeKey.stringKey(name)))

  protected def longAttribute(attributes: JavaAttributes, name: String): Option[Long] =
    Option(attributes.get(JavaAttributeKey.longKey(name))).map(_.longValue())

  protected def booleanAttribute(attributes: JavaAttributes, name: String): Option[Boolean] =
    Option(attributes.get(JavaAttributeKey.booleanKey(name))).map(_.booleanValue())

  protected def assertStringAttribute(span: SpanData, expected: Attribute[String]): Unit =
    stringAttribute(span.getAttributes, expected.key.name) shouldBe Some(expected.value)

  protected def assertLongAttribute(span: SpanData, expected: Attribute[Long]): Unit =
    longAttribute(span.getAttributes, expected.key.name) shouldBe Some(expected.value)

  protected final class StubPublishingProgram(
      published: Ref[IO, Vector[Published]],
      listeners: Ref[IO, Vector[PublishReturn => IO[Unit]]],
      onPublish: Published => IO[Unit] = _ => IO.unit
  ) extends PublishingProgram[IO] {

    private def record(value: Published): IO[Unit] = published.update(_ :+ value) *> onPublish(value)

    override def basicPublish(
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        message: AmqpMessage[Array[Byte]]
    ): IO[Unit] = record(Published(exchangeName, routingKey, None, message))

    override def basicPublishWithFlag(
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flag: PublishingFlag,
        message: AmqpMessage[Array[Byte]]
    ): IO[Unit] = record(Published(exchangeName, routingKey, Some(flag), message))

    override def addPublishingListener(channel: AMQPChannel, listener: PublishReturn => IO[Unit]): IO[Unit] =
      listeners.update(_ :+ listener)

    override def clearPublishingListeners(channel: AMQPChannel): IO[Unit] = listeners.set(Vector.empty)

    override def createPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
        encoder: MessageEncoder[IO, A]
    ): IO[A => IO[Unit]] =
      IO.pure(value => encoder.run(value).flatMap(basicPublish(channel, exchangeName, routingKey, _)))

    override def createPublisherWithListener[A](
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flag: PublishingFlag,
        listener: PublishReturn => IO[Unit]
    )(implicit encoder: MessageEncoder[IO, A]): IO[A => IO[Unit]] =
      addPublishingListener(channel, listener).as(value =>
        encoder.run(value).flatMap(basicPublishWithFlag(channel, exchangeName, routingKey, flag, _))
      )

    override def createRoutingPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName)(implicit
        encoder: MessageEncoder[IO, A]
    ): IO[RoutingKey => A => IO[Unit]] =
      IO.pure(routingKey => value => encoder.run(value).flatMap(basicPublish(channel, exchangeName, routingKey, _)))

    override def createRoutingPublisherWithListener[A](
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        flag: PublishingFlag,
        listener: PublishReturn => IO[Unit]
    )(implicit encoder: MessageEncoder[IO, A]): IO[RoutingKey => A => IO[Unit]] =
      addPublishingListener(channel, listener).as(routingKey =>
        value => encoder.run(value).flatMap(basicPublishWithFlag(channel, exchangeName, routingKey, flag, _))
      )

    override def createBasicPublisher[A](channel: AMQPChannel)(implicit
        encoder: MessageEncoder[IO, A]
    ): IO[(ExchangeName, RoutingKey, A) => IO[Unit]] =
      IO.pure((exchangeName, routingKey, value) =>
        encoder.run(value).flatMap(basicPublish(channel, exchangeName, routingKey, _))
      )

    override def createBasicPublisherWithListener[A](
        channel: AMQPChannel,
        flag: PublishingFlag,
        listener: PublishReturn => IO[Unit]
    )(implicit encoder: MessageEncoder[IO, A]): IO[(ExchangeName, RoutingKey, A) => IO[Unit]] =
      addPublishingListener(channel, listener).as((exchangeName, routingKey, value) =>
        encoder.run(value).flatMap(basicPublishWithFlag(channel, exchangeName, routingKey, flag, _))
      )
  }

  protected final class StubConsumingProgram(
      source: Stream[IO, AmqpEnvelope[String]],
      acked: Ref[IO, Vector[(AckResult, AckMultiple)]]
  ) extends AckConsumingProgram[IO] {

    private def stream[A]: Stream[IO, AmqpEnvelope[A]] =
      source.asInstanceOf[Stream[IO, AmqpEnvelope[A]]]

    override def createAckerConsumer[A](
        channel: AMQPChannel,
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs],
        ackMultiple: AckMultiple
    )(implicit decoder: EnvelopeDecoder[IO, A]): IO[(AckResult => IO[Unit], Stream[IO, AmqpEnvelope[A]])] =
      IO.pure((result => acked.update(_ :+ (result -> ackMultiple)), stream[A]))

    override def createAckerConsumerWithMultipleFlag[A](
        channel: AMQPChannel,
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs]
    )(implicit
        decoder: EnvelopeDecoder[IO, A]
    ): IO[((AckResult, AckMultiple) => IO[Unit], Stream[IO, AmqpEnvelope[A]])] =
      IO.pure(((result, multiple) => acked.update(_ :+ (result -> multiple)), stream[A]))

    override def createAutoAckConsumer[A](
        channel: AMQPChannel,
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs]
    )(implicit decoder: EnvelopeDecoder[IO, A]): IO[Stream[IO, AmqpEnvelope[A]]] = IO.pure(stream[A])

    override def createAcker(channel: AMQPChannel, ackMultiple: AckMultiple): IO[AckResult => IO[Unit]] =
      IO.pure(result => acked.update(_ :+ (result -> ackMultiple)))

    override def createAckerWithMultipleFlag(channel: AMQPChannel): IO[(AckResult, AckMultiple) => IO[Unit]] =
      IO.pure((result, multiple) => acked.update(_ :+ (result -> multiple)))

    override def createConsumer[A](
        queueName: QueueName,
        channel: AMQPChannel,
        basicQos: BasicQos,
        autoAck: Boolean,
        noLocal: Boolean,
        exclusive: Boolean,
        consumerTag: ConsumerTag,
        args: Arguments
    )(implicit decoder: EnvelopeDecoder[IO, A]): IO[Stream[IO, AmqpEnvelope[A]]] = IO.pure(stream[A])

    override def basicCancel(channel: AMQPChannel, consumerTag: ConsumerTag): IO[Unit] = IO.unit
  }
}
