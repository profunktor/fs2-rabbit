/*
 * Copyright 2017-2020 ProfunKtor
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
import cats.implicits._
import com.rabbitmq.client.{DefaultSaslConfig, SaslConfig}
import dev.profunktor.fs2rabbit.algebra._
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import dev.profunktor.fs2rabbit.algebra.AckConsumingStream.AckConsumingStream
import dev.profunktor.fs2rabbit.algebra.ConnectionResource.ConnectionResource
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.program._
import fs2.Stream
import javax.net.ssl.SSLContext

object RabbitClient {

  def apply[F[_]: ConcurrentEffect: ContextShift](
      config: Fs2RabbitConfig,
      blocker: Blocker,
      sslContext: Option[SSLContext] = None,
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConfig: SaslConfig = DefaultSaslConfig.PLAIN
  ): F[RabbitClient[F]] = {

    val internalQ         = new LiveInternalQueue[F](config.internalQueueSize.getOrElse(500))
    val connection        = ConnectionResource.make(config, sslContext, saslConfig)
    val consumingProgram  = AckConsumingProgram.make[F](config, internalQ)
    val publishingProgram = PublishingProgram.make[F](blocker)

    (connection, consumingProgram, publishingProgram).mapN {
      case (conn, consuming, publish) =>
        val consumeClient     = Consume.make[F]
        val publishClient     = Publish.make[F](blocker)
        val bindingClient     = Binding.make[F]
        val declarationClient = Declaration.make[F]
        val deletionClient    = Deletion.make[F]

        new RabbitClient[F](
          conn,
          consumeClient,
          publishClient,
          bindingClient,
          declarationClient,
          deletionClient,
          consuming,
          publish
        )
    }
  }
}

class RabbitClient[F[_]: Concurrent] private[fs2rabbit] (
    connection: ConnectionResource[F],
    consume: Consume[F],
    publish: Publish[F],
    binding: Binding[F],
    declaration: Declaration[F],
    deletion: Deletion[F],
    consumingProgram: AckConsumingStream[F],
    publishingProgram: Publishing[F]
) {

  def createChannel(conn: AMQPConnection): Resource[F, AMQPChannel] =
    connection.createChannel(conn)

  def createConnection: Resource[F, AMQPConnection] =
    connection.createConnection

  def createConnectionChannel: Resource[F, AMQPChannel] =
    createConnection.flatMap(createChannel)

  def createAckerConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(
      implicit channel: AMQPChannel,
      decoder: EnvelopeDecoder[F, A]
  ): F[(AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] =
    consumingProgram.createAckerConsumer(
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

  def createPublisher[A](exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel,
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

  def createBasicPublisher[A](
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    publishingProgram.createBasicPublisher(channel)

  def createBasicPublisherWithListener[A](flag: PublishingFlag, listener: PublishReturn => F[Unit])(
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    publishingProgram.createBasicPublisherWithListener(
      channel,
      flag,
      listener
    )

  def createRoutingPublisher[A](exchangeName: ExchangeName)(
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[RoutingKey => A => F[Unit]] =
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
    publish.addPublishingListener(channel, listener)

  def clearPublishingListeners(implicit channel: AMQPChannel): F[Unit] =
    publish.clearPublishingListeners(channel)

  def basicCancel(
      consumerTag: ConsumerTag
  )(implicit channel: AMQPChannel): F[Unit] =
    consume.basicCancel(channel, consumerTag)

  def bindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindQueue(channel, queueName, exchangeName, routingKey)

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

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: AMQPChannel
  ): F[Unit] =
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
