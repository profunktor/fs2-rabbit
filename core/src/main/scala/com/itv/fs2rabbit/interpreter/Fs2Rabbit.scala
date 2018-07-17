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

package com.itv.fs2rabbit.interpreter

import cats.effect.{Concurrent, ConcurrentEffect}
import cats.syntax.applicative._
import com.itv.fs2rabbit.algebra.{AMQPClient, Connection}
import com.itv.fs2rabbit.config.Fs2RabbitConfig
import com.itv.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.itv.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.itv.fs2rabbit.model._
import com.itv.fs2rabbit.program._
import com.itv.fs2rabbit.algebra.Connection
import com.itv.fs2rabbit.model._
import com.itv.fs2rabbit.program.{AckerConsumerProgram, PublishingProgram}
import fs2.Stream

// $COVERAGE-OFF$
object Fs2Rabbit {
  def apply[F[_]: ConcurrentEffect](config: Fs2RabbitConfig): F[Fs2Rabbit[F]] = {
    val amqpClient    = new AMQPClientStream[F]
    val connStream    = new ConnectionStream[F](config)
    val ackerConsumer = new AckerConsumerProgram[F](config, amqpClient)
    new Fs2Rabbit[F](config, connStream, amqpClient, ackerConsumer).pure[F]
  }
}
// $COVERAGE-ON$

class Fs2Rabbit[F[_]: Concurrent](config: Fs2RabbitConfig,
                                  connectionStream: Connection[Stream[F, ?]],
                                  amqpClient: AMQPClient[Stream[F, ?], F],
                                  ackerConsumerProgram: AckerConsumerProgram[F]) {

  private[fs2rabbit] val consumingProgram: ConsumingProgram[F] =
    new ConsumingProgram[F](ackerConsumerProgram)

  private[fs2rabbit] val publishingProgram: PublishingProgram[F] =
    new PublishingProgram[F](amqpClient)

  def createConnectionChannel: Stream[F, AMQPChannel] = connectionStream.createConnectionChannel

  def createAckerConsumer(queueName: QueueName,
                          basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                          consumerArgs: Option[ConsumerArgs] = None)(
      implicit channel: AMQPChannel): Stream[F, (StreamAcker[F], StreamConsumer[F])] =
    consumingProgram.createAckerConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createAutoAckConsumer(
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None)(implicit channel: AMQPChannel): Stream[F, StreamConsumer[F]] =
    consumingProgram.createAutoAckConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createPublisher(exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, StreamPublisher[F]] =
    publishingProgram.createPublisher(channel.value, exchangeName, routingKey)

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
    amqpClient.unbindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindExchange(channel.value, destination, source, routingKey, args)

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchange(channel.value, exchangeName, exchangeType)

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
