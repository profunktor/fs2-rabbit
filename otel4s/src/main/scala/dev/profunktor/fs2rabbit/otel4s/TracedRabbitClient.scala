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

import cats.effect.Concurrent
import cats.syntax.all.*
import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.*
import dev.profunktor.fs2rabbit.otel4s.instances.*
import dev.profunktor.fs2rabbit.otel4s.internal.Semconv
import org.typelevel.otel4s.trace.{SpanKind, Tracer}

trait TracedRabbitClient[F[_]] {
  def underlying: RabbitClient[F]

  def createPublisher[A](exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
      channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[A => F[Unit]]

  def createPublisherWithListener[A](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[A => F[Unit]]

  def createRoutingPublisher[A](exchangeName: ExchangeName)(implicit
      channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[RoutingKey => A => F[Unit]]

  def createRoutingPublisherWithListener[A](
      exchangeName: ExchangeName,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]]

  def createBasicPublisher[A](implicit
      channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]]

  def createBasicPublisherWithListener[A](flag: PublishingFlag, listener: PublishReturn => F[Unit])(implicit
      channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]]

  def createAckerConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None,
      ackMultiple: AckMultiple = AckMultiple(false)
  )(implicit
      channel: AMQPChannel,
      decoder: EnvelopeDecoder[F, A]
  ): F[(AckResult => F[Unit], TracedRabbitConsumer[F, A])]

  def createAckerConsumerWithMultipleFlag[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit
      channel: AMQPChannel,
      decoder: EnvelopeDecoder[F, A]
  ): F[((AckResult, AckMultiple) => F[Unit], TracedRabbitConsumer[F, A])]

  def createAutoAckConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit channel: AMQPChannel, decoder: EnvelopeDecoder[F, A]): F[TracedRabbitConsumer[F, A]]
}

object TracedRabbitClient {

  def noop[F[_]: Concurrent](underlying: RabbitClient[F]): TracedRabbitClient[F] =
    new Noop[F](underlying)

  final private[otel4s] class Impl[F[_]: Concurrent: Tracer](
      override val underlying: RabbitClient[F],
      config: RabbitTracer.Config
  ) extends TracedRabbitClient[F] {

    override def createPublisher[A](exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[A => F[Unit]] =
      Concurrent[F].pure(message => publish(channel, exchangeName, routingKey, None, message))

    override def createPublisherWithListener[A](
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flag: PublishingFlag,
        listener: PublishReturn => F[Unit]
    )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[A => F[Unit]] =
      underlying
        .addPublishingListener(listener)
        .as(message => publish(channel, exchangeName, routingKey, Some(flag), message))

    override def createRoutingPublisher[A](exchangeName: ExchangeName)(implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[RoutingKey => A => F[Unit]] =
      Concurrent[F].pure(routingKey => message => publish(channel, exchangeName, routingKey, None, message))

    override def createRoutingPublisherWithListener[A](
        exchangeName: ExchangeName,
        flag: PublishingFlag,
        listener: PublishReturn => F[Unit]
    )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]] =
      underlying.addPublishingListener(listener).as { routingKey => message =>
        publish(channel, exchangeName, routingKey, Some(flag), message)
      }

    override def createBasicPublisher[A](implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
      Concurrent[F].pure((exchangeName, routingKey, message) =>
        publish(channel, exchangeName, routingKey, None, message)
      )

    override def createBasicPublisherWithListener[A](flag: PublishingFlag, listener: PublishReturn => F[Unit])(implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
      underlying.addPublishingListener(listener).as { (exchangeName, routingKey, message) =>
        publish(channel, exchangeName, routingKey, Some(flag), message)
      }

    override def createAckerConsumer[A](
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs],
        ackMultiple: AckMultiple
    )(implicit
        channel: AMQPChannel,
        decoder: EnvelopeDecoder[F, A]
    ): F[(AckResult => F[Unit], TracedRabbitConsumer[F, A])] =
      underlying
        .createAckerConsumer(queueName, basicQos, consumerArgs, ackMultiple)
        .map { case (acker, stream) => (acker, TracedRabbitConsumer(queueName, stream, config)) }

    override def createAckerConsumerWithMultipleFlag[A](
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs]
    )(implicit
        channel: AMQPChannel,
        decoder: EnvelopeDecoder[F, A]
    ): F[((AckResult, AckMultiple) => F[Unit], TracedRabbitConsumer[F, A])] =
      underlying
        .createAckerConsumerWithMultipleFlag(queueName, basicQos, consumerArgs)
        .map { case (acker, stream) => (acker, TracedRabbitConsumer(queueName, stream, config)) }

    override def createAutoAckConsumer[A](
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs]
    )(implicit channel: AMQPChannel, decoder: EnvelopeDecoder[F, A]): F[TracedRabbitConsumer[F, A]] =
      underlying
        .createAutoAckConsumer(queueName, basicQos, consumerArgs)
        .map(stream => TracedRabbitConsumer(queueName, stream, config))

    private def publish[A](
        channel: AMQPChannel,
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flag: Option[PublishingFlag],
        value: A
    )(implicit encoder: MessageEncoder[F, A]): F[Unit] =
      encoder.run(value).flatMap { message =>
        val spanContext = Semconv.publishSpanContext(exchangeName, routingKey, message)
        val spanSetup   = config.publishSpanSetup(spanContext)

        Tracer[F]
          .joinOrRoot(message.properties.headers)(Tracer[F].currentSpanContext)
          .flatMap { creationContext =>
            val spanKind: SpanKind = creationContext.fold[SpanKind](SpanKind.Producer)(_ => SpanKind.Client)

            val builder = Tracer[F]
              .spanBuilder(spanSetup.spanName)
              .withSpanKind(spanKind)
              .withFinalizationStrategy(spanSetup.finalizationStrategy)
              .addAttributes(
                Semconv.publishAttributes(spanContext, config.clientId) ++
                  config.constAttributes ++
                  spanSetup.attributes
              )

            creationContext
              .fold(builder)(context => builder.addLink(context, Semconv.publishLinkAttributes(spanContext)))
              .build
              .surround {
                val publishedMessage = creationContext.fold(
                  Tracer[F]
                    .propagate(message.properties.headers)
                    .map(headers => message.copy(properties = message.properties.copy(headers = headers)))
                )(_ => Concurrent[F].pure(message))

                publishedMessage.flatMap { tracedMessage =>
                  flag.fold(
                    underlying.publishingProgram.basicPublish(channel, exchangeName, routingKey, tracedMessage)
                  )(publishingFlag =>
                    underlying.publishingProgram.basicPublishWithFlag(
                      channel,
                      exchangeName,
                      routingKey,
                      publishingFlag,
                      tracedMessage
                    )
                  )
                }
              }
          }
      }
  }

  final private class Noop[F[_]: Concurrent](override val underlying: RabbitClient[F]) extends TracedRabbitClient[F] {
    override def createPublisher[A](exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[A => F[Unit]] = underlying.createPublisher(exchangeName, routingKey)

    override def createPublisherWithListener[A](
        exchangeName: ExchangeName,
        routingKey: RoutingKey,
        flag: PublishingFlag,
        listener: PublishReturn => F[Unit]
    )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[A => F[Unit]] =
      underlying.createPublisherWithListener(exchangeName, routingKey, flag, listener)

    override def createRoutingPublisher[A](exchangeName: ExchangeName)(implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[RoutingKey => A => F[Unit]] = underlying.createRoutingPublisher(exchangeName)

    override def createRoutingPublisherWithListener[A](
        exchangeName: ExchangeName,
        flag: PublishingFlag,
        listener: PublishReturn => F[Unit]
    )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]] =
      underlying.createRoutingPublisherWithListener(exchangeName, flag, listener)

    override def createBasicPublisher[A](implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[(ExchangeName, RoutingKey, A) => F[Unit]] = underlying.createBasicPublisher

    override def createBasicPublisherWithListener[A](flag: PublishingFlag, listener: PublishReturn => F[Unit])(implicit
        channel: AMQPChannel,
        encoder: MessageEncoder[F, A]
    ): F[(ExchangeName, RoutingKey, A) => F[Unit]] = underlying.createBasicPublisherWithListener(flag, listener)

    override def createAckerConsumer[A](
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs],
        ackMultiple: AckMultiple
    )(implicit
        channel: AMQPChannel,
        decoder: EnvelopeDecoder[F, A]
    ): F[(AckResult => F[Unit], TracedRabbitConsumer[F, A])] =
      underlying
        .createAckerConsumer(queueName, basicQos, consumerArgs, ackMultiple)
        .map { case (acker, stream) => (acker, TracedRabbitConsumer.noop(queueName, stream)) }

    override def createAckerConsumerWithMultipleFlag[A](
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs]
    )(implicit
        channel: AMQPChannel,
        decoder: EnvelopeDecoder[F, A]
    ): F[((AckResult, AckMultiple) => F[Unit], TracedRabbitConsumer[F, A])] =
      underlying
        .createAckerConsumerWithMultipleFlag(queueName, basicQos, consumerArgs)
        .map { case (acker, stream) => (acker, TracedRabbitConsumer.noop(queueName, stream)) }

    override def createAutoAckConsumer[A](
        queueName: QueueName,
        basicQos: BasicQos,
        consumerArgs: Option[ConsumerArgs]
    )(implicit channel: AMQPChannel, decoder: EnvelopeDecoder[F, A]): F[TracedRabbitConsumer[F, A]] =
      underlying
        .createAutoAckConsumer(queueName, basicQos, consumerArgs)
        .map(stream => TracedRabbitConsumer.noop(queueName, stream))
  }
}
