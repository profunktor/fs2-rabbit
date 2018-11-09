/*
 * Copyright 2017 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.interpreter

import javax.net.ssl.SSLContext

import cats.effect.{Concurrent, ConcurrentEffect}
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.effects.EnvelopeDecoder
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import fs2.Stream

// $COVERAGE-OFF$
object Fs2Rabbit {
  def apply[F[_]: ConcurrentEffect, A](
      config: Fs2RabbitConfig,
      sslContext: Option[SSLContext] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Fs2Rabbit[F, A]] =
    ConnectionStream.mkConnectionFactory[F](config, sslContext).map { factory =>
      val amqpClient = new AMQPClientStream[F, A]
      val connStream = new ConnectionStream[F](factory)
      val acker      = new AckerProgram[F, A](config, amqpClient)
      val consumer   = new ConsumerProgram[F, A](amqpClient)
      new Fs2Rabbit[F, A](config, connStream, amqpClient, acker, consumer)
    }
}
// $COVERAGE-ON$

private[fs2rabbit] class Fs2Rabbit[F[_]: Concurrent, A](
    config: Fs2RabbitConfig,
    connectionStream: Connection[Stream[F, ?]],
    amqpClient: AMQPClient[Stream[F, ?], F, A],
    acker: Acker[Stream[F, ?]],
    consumer: Consumer[Stream[F, ?], A]
) {

  private[fs2rabbit] val consumingProgram: Consuming[Stream[F, ?], A] =
    new ConsumingProgram[F, A](acker, consumer)

  private[fs2rabbit] val publishingProgram: Publishing[Stream[F, ?], F] =
    new PublishingProgram[F, A](amqpClient)

  def createConnectionChannel: Stream[F, AMQPChannel] = connectionStream.createConnectionChannel

  def createAckerConsumer(
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit channel: AMQPChannel): Stream[F, (StreamAcker[F], StreamConsumer[F, A])] =
    consumingProgram.createAckerConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createAutoAckConsumer(
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit channel: AMQPChannel): Stream[F, StreamConsumer[F, A]] =
    consumingProgram.createAutoAckConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createPublisher(exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, StreamPublisher[F]] =
    publishingProgram.createPublisher(channel.value, exchangeName, routingKey)

  def createPublisherWithListener(
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flags: PublishingFlag,
      listener: PublishingListener[F]
  )(implicit channel: AMQPChannel): Stream[F, StreamPublisher[F]] =
    publishingProgram.createPublisherWithListener(channel.value, exchangeName, routingKey, flags, listener)

  def addPublishingListener(listener: PublishingListener[F])(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.addPublishingListener(channel.value, listener)

  def clearPublishingListeners(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.clearPublishingListeners(channel.value)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueueNoWait(channel.value, queueName, exchangeName, routingKey, args)

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    unbindQueue(queueName, exchangeName, routingKey, QueueUnbindArgs(Map.empty))

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueUnbindArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.unbindQueue(channel.value, queueName, exchangeName, routingKey, args)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindExchange(channel.value, destination, source, routingKey, args)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    bindExchange(destination, source, routingKey, ExchangeBindingArgs(Map.empty))

  def bindExchangeNoWait(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  )(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindExchangeNoWait(channel.value, destination, source, routingKey, args)

  def unbindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeUnbindArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.unbindExchange(channel.value, destination, source, routingKey, args)

  def unbindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    unbindExchange(destination, source, routingKey, ExchangeUnbindArgs(Map.empty))

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    declareExchange(DeclarationExchangeConfig.default(exchangeName, exchangeType))

  def declareExchange(exchangeConfig: DeclarationExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchange(channel.value, exchangeConfig)

  def declareExchangeNoWait(exchangeConfig: DeclarationExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchangeNoWait(channel.value, exchangeConfig)

  def declareExchangePassive(exchangeName: ExchangeName)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchangePassive(channel.value, exchangeName)

  def declareQueue(queueConfig: DeclarationQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareQueue(channel.value, queueConfig)

  def declareQueueNoWait(queueConfig: DeclarationQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareQueueNoWait(channel.value, queueConfig)

  def declareQueuePassive(queueName: QueueName)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareQueuePassive(channel.value, queueName)

  def deleteQueue(config: DeletionQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteQueue(channel.value, config)

  def deleteQueueNoWait(config: DeletionQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteQueueNoWait(channel.value, config)

  def deleteExchange(config: DeletionExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteExchange(channel.value, config)

  def deleteExchangeNoWait(config: DeletionExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteExchangeNoWait(channel.value, config)

}
