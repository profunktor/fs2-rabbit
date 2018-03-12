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

import cats.effect.{Effect, IO}
import cats.syntax.all._
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, Connection}
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import fs2.Stream
import fs2.async.mutable.Queue

import scala.concurrent.ExecutionContext

// $COVERAGE-OFF$
object Fs2Rabbit {
  def apply[F[_]](config: Fs2RabbitConfig)(implicit F: Effect[F], ec: ExecutionContext): F[Fs2Rabbit[F]] =
    for {
      internalQ  <- F.liftIO(fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](500))
      amqpClient <- F.delay(new AmqpClientStream[F](internalQ))
      connStream <- F.delay(new ConnectionStream[F](config))
      fs2Rabbit  <- F.delay(new Fs2Rabbit[F](config, connStream, internalQ)(F, amqpClient, ec))
    } yield fs2Rabbit
}
// $COVERAGE-ON$

class Fs2Rabbit[F[_]](config: Fs2RabbitConfig,
                      connectionStream: Connection[Stream[F, ?]],
                      internalQ: Queue[IO, Either[Throwable, AmqpEnvelope]])(implicit F: Effect[F],
                                                                             amqpClient: AMQPClient[Stream[F, ?]],
                                                                             EC: ExecutionContext) {

  private implicit val ackerConsumerProgram: AckerConsumerProgram[F] =
    new AckerConsumerProgram[F](internalQ, config)

  private val consumingProgram: ConsumingProgram[F] =
    new ConsumingProgram[F]

  private val publishingProgram: PublishingProgram[F] =
    new PublishingProgram[F]

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
