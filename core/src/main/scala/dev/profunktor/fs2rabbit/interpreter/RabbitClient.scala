/*
 * Copyright 2017-2024 ProfunKtor
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

package dev.profunktor.fs2rabbit.interpreter

import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import com.rabbitmq.client.DefaultSaslConfig
import com.rabbitmq.client.MetricsCollector
import com.rabbitmq.client.SaslConfig
import dev.profunktor.fs2rabbit.algebra.ConnectionResource.ConnectionResource
import dev.profunktor.fs2rabbit.algebra._
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.config.declaration.DeclarationExchangeConfig
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.config.deletion.DeletionExchangeConfig
import dev.profunktor.fs2rabbit.config.deletion.DeletionQueueConfig
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.program._
import fs2.Stream

import java.util.concurrent.ThreadFactory
import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext

object RabbitClient {
  @deprecated(message = "Use `default` to create Builder instead", since = "5.0.0")
  def apply[F[_]: Async](
      config: Fs2RabbitConfig,
      dispatcher: Dispatcher[F],
      sslContext: Option[SSLContext] = None,
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConfig: SaslConfig = DefaultSaslConfig.PLAIN,
      metricsCollector: Option[MetricsCollector] = None,
      threadFactory: Option[F[ThreadFactory]] = None
  ): F[RabbitClient[F]] = {
    val internalQ         = new LiveInternalQueue[F](config.internalQueueSize.getOrElse(500))
    val connection        = ConnectionResource.make(config, sslContext, saslConfig, metricsCollector, threadFactory)
    val consumingProgram  = AckConsumingProgram.make[F](config, internalQ, dispatcher)
    val publishingProgram = PublishingProgram.make[F](dispatcher)
    val bindingClient     = Binding.make[F]
    val declarationClient = Declaration.make[F]
    val deletionClient    = Deletion.make[F]

    connection.map { conn =>
      new RabbitClient[F](
        conn,
        bindingClient,
        declarationClient,
        deletionClient,
        consumingProgram,
        publishingProgram
      )
    }
  }

  @deprecated(message = "Use `default` to create Builder instead", since = "5.0.0")
  def resource[F[_]: Async](
      config: Fs2RabbitConfig,
      sslContext: Option[SSLContext] = None,
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConfig: SaslConfig = DefaultSaslConfig.PLAIN,
      metricsCollector: Option[MetricsCollector] = None,
      threadFactory: Option[F[ThreadFactory]] = None
  ): Resource[F, RabbitClient[F]] = Dispatcher.parallel[F](await = false).evalMap { dispatcher =>
    apply[F](config, dispatcher, sslContext, saslConfig, metricsCollector, threadFactory)
  }

  sealed abstract class Builder[F[_]: Async] private[RabbitClient] (
      config: Fs2RabbitConfig,
      sslContext: Option[SSLContext],
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConfig: SaslConfig,
      metricsCollector: Option[MetricsCollector],
      threadFactory: Option[F[ThreadFactory]],
      executionContext: Option[F[ExecutionContext]]
  ) {
    private def copy(
        config: Fs2RabbitConfig = config,
        sslContext: Option[SSLContext] = sslContext,
        saslConfig: SaslConfig = saslConfig,
        metricsCollector: Option[MetricsCollector] = metricsCollector,
        threadFactory: Option[F[ThreadFactory]] = threadFactory,
        executionContext: Option[F[ExecutionContext]] = executionContext
    ): Builder[F] = new Builder[F](
      config = config,
      sslContext = sslContext,
      saslConfig = saslConfig,
      metricsCollector = metricsCollector,
      threadFactory = threadFactory,
      executionContext = executionContext
    ) {}

    def withSslContext(sslContext: SSLContext): Builder[F] = copy(sslContext = Some(sslContext))

    def withSaslConfig(saslConfig: SaslConfig): Builder[F] = copy(saslConfig = saslConfig)

    def withMetricsCollector(metricsCollector: MetricsCollector): Builder[F] =
      copy(metricsCollector = Some(metricsCollector))

    def withThreadFactory(threadFactory: F[ThreadFactory]): Builder[F] = copy(threadFactory = Some(threadFactory))

    def withExecutionContext(executionContext: F[ExecutionContext]): Builder[F] =
      copy(executionContext = Some(executionContext))

    def build(dispatcher: Dispatcher[F]): F[RabbitClient[F]] =
      create[F](config, dispatcher, sslContext, saslConfig, metricsCollector, threadFactory, executionContext)

    def resource: Resource[F, RabbitClient[F]] =
      Dispatcher.parallel[F](await = false).evalMap(build)
  }

  def default[F[_]: Async](
      config: Fs2RabbitConfig
  ): Builder[F] = new Builder[F](
    config = config,
    sslContext = None,
    saslConfig = DefaultSaslConfig.PLAIN,
    metricsCollector = None,
    threadFactory = None,
    executionContext = None
  ) {}

  private def create[F[_]: Async](
      config: Fs2RabbitConfig,
      dispatcher: Dispatcher[F],
      sslContext: Option[SSLContext],
      saslConfig: SaslConfig,
      metricsCollector: Option[MetricsCollector],
      threadFactory: Option[F[ThreadFactory]],
      executionContext: Option[F[ExecutionContext]]
  ): F[RabbitClient[F]] = {
    val internalQ         = new LiveInternalQueue[F](config.internalQueueSize.getOrElse(500))
    val connection        = executionContext.getOrElse(Async[F].executionContext).flatMap { executionContext =>
      ConnectionResource.make(config, executionContext, sslContext, saslConfig, metricsCollector, threadFactory)
    }
    val consumingProgram  = AckConsumingProgram.make[F](config, internalQ, dispatcher)
    val publishingProgram = PublishingProgram.make[F](dispatcher)
    val bindingClient     = Binding.make[F]
    val declarationClient = Declaration.make[F]
    val deletionClient    = Deletion.make[F]

    connection.map { conn =>
      new RabbitClient[F](
        conn,
        bindingClient,
        declarationClient,
        deletionClient,
        consumingProgram,
        publishingProgram
      )
    }
  }

  implicit def toRabbitClientOps[F[_]](client: RabbitClient[F]): RabbitClientOps[F] = new RabbitClientOps[F](client)
}

class RabbitClient[F[_]] private[fs2rabbit] (
    val connection: ConnectionResource[F],
    val binding: Binding[F],
    val declaration: Declaration[F],
    val deletion: Deletion[F],
    val consumingProgram: AckConsumingProgram[F],
    val publishingProgram: PublishingProgram[F]
) {

  def createChannel(conn: AMQPConnection): Resource[F, AMQPChannel] =
    connection.createChannel(conn)

  def createConnection: Resource[F, AMQPConnection] =
    connection.createConnection

  def createConnectionChannel: Resource[F, AMQPChannel] =
    createConnection.flatMap(createChannel)

  /** @param ackMultiple
    *   configures the behaviour of the returned acking function. If true (n)acks all messages up to and including the
    *   supplied delivery tag, if false (n)acks just the supplied delivery tag.
    */
  def createAckerConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None,
      ackMultiple: AckMultiple = AckMultiple(false)
  )(implicit
      channel: AMQPChannel,
      decoder: EnvelopeDecoder[F, A]
  ): F[(AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] =
    consumingProgram.createAckerConsumer(
      channel,
      queueName,
      basicQos,
      consumerArgs,
      ackMultiple
    )

  /** Returns an acking function and a stream of messages. The acking function takes two arguments - the first is the
    * {@link AckResult} that wraps a delivery tag, the second is a {@link AckMultiple} flag that if true (n)acks all
    * messages up to and including the supplied delivery tag, if false (n)acks just the supplied delivery tag.
    */
  def createAckerConsumerWithMultipleFlag[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit
      channel: AMQPChannel,
      decoder: EnvelopeDecoder[F, A]
  ): F[((AckResult, AckMultiple) => F[Unit], Stream[F, AmqpEnvelope[A]])] =
    consumingProgram.createAckerConsumerWithMultipleFlag(
      channel,
      queueName,
      basicQos,
      consumerArgs
    )

  def createAutoAckConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit channel: AMQPChannel, decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] =
    consumingProgram.createAutoAckConsumer(
      channel,
      queueName,
      basicQos,
      consumerArgs
    )

  def createPublisher[A](exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
      channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[A => F[Unit]] =
    publishingProgram.createPublisher(channel, exchangeName, routingKey)

  def createPublisherWithListener[A](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[A => F[Unit]] =
    publishingProgram.createPublisherWithListener(
      channel,
      exchangeName,
      routingKey,
      flags,
      listener
    )

  def createBasicPublisher[A](implicit
      channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    publishingProgram.createBasicPublisher(channel)

  def createBasicPublisherWithListener[A](flag: PublishingFlag, listener: PublishReturn => F[Unit])(implicit
      channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    publishingProgram.createBasicPublisherWithListener(
      channel,
      flag,
      listener
    )

  def createRoutingPublisher[A](
      exchangeName: ExchangeName
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]] =
    publishingProgram.createRoutingPublisher(channel, exchangeName)

  def createRoutingPublisherWithListener[A](
      exchangeName: ExchangeName,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]] =
    publishingProgram.createRoutingPublisherWithListener(
      channel,
      exchangeName,
      flags,
      listener
    )

  def addPublishingListener(
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel): F[Unit] =
    publishingProgram.addPublishingListener(channel, listener)

  def clearPublishingListeners(implicit channel: AMQPChannel): F[Unit] =
    publishingProgram.clearPublishingListeners(channel)

  def basicCancel(
      consumerTag: ConsumerTag
  )(implicit channel: AMQPChannel): F[Unit] =
    consumingProgram.basicCancel(channel, consumerTag)

  def bindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))

  def bindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindQueue(channel, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindQueueNoWait(
      channel,
      queueName,
      exchangeName,
      routingKey,
      args
    )

  def unbindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    unbindQueue(queueName, exchangeName, routingKey, QueueUnbindArgs(Map.empty))

  def unbindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueUnbindArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.unbindQueue(
      channel,
      queueName,
      exchangeName,
      routingKey,
      args
    )

  def bindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindExchange(channel, destination, source, routingKey, args)

  def bindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    bindExchange(
      destination,
      source,
      routingKey,
      ExchangeBindingArgs(Map.empty)
    )

  def bindExchangeNoWait(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindExchangeNoWait(
      channel,
      destination,
      source,
      routingKey,
      args
    )

  def unbindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeUnbindArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.unbindExchange(channel, destination, source, routingKey, args)

  def unbindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    unbindExchange(
      destination,
      source,
      routingKey,
      ExchangeUnbindArgs(Map.empty)
    )

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(implicit channel: AMQPChannel): F[Unit] =
    declareExchange(
      DeclarationExchangeConfig.default(exchangeName, exchangeType)
    )

  def declareExchange(
      exchangeConfig: DeclarationExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareExchange(channel, exchangeConfig)

  def declareExchangeNoWait(
      exchangeConfig: DeclarationExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareExchangeNoWait(channel, exchangeConfig)

  def declareExchangePassive(
      exchangeName: ExchangeName
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareExchangePassive(channel, exchangeName)

  def declareQueue(implicit channel: AMQPChannel): F[QueueName] =
    declaration.declareQueue(channel)

  def declareQueue(
      queueConfig: DeclarationQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareQueue(channel, queueConfig)

  def declareQueueNoWait(
      queueConfig: DeclarationQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareQueueNoWait(channel, queueConfig)

  def declareQueuePassive(
      queueName: QueueName
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareQueuePassive(channel, queueName)

  def deleteQueue(
      config: DeletionQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteQueue(channel, config)

  def deleteQueueNoWait(
      config: DeletionQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteQueueNoWait(channel, config)

  def deleteExchange(
      config: DeletionExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteExchange(channel, config)

  def deleteExchangeNoWait(
      config: DeletionExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteExchangeNoWait(channel, config)
}
