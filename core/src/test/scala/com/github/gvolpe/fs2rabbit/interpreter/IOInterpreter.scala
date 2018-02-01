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

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.algebra.AMQPClient
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.instances.streameval._
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import fs2.Stream
import fs2.async.mutable.{Queue => Fs2Queue}

class IOInterpreter(config: IO[Fs2RabbitConfig],
                    testQ: Fs2Queue[IO, Either[Throwable, AmqpEnvelope]],
                    ackerQ: Fs2Queue[IO, AckResult]) {

  private implicit val amqpClient: AMQPClient[Stream[IO, ?]] =
    new AMQPClientInMemory(testQ, ackerQ)

  private val connectionStub: ConnectionStub = new ConnectionStub()

  private implicit val ackerConsumerProgram: AckerConsumerProgram[IO] =
    new AckerConsumerProgram[IO](testQ, config)

  private val consumingProgram = new ConsumingProgram[IO]

  private val publishingProgram = new PublishingProgram[IO]

  def createConnectionChannel: Stream[IO, AMQPChannel] =
    connectionStub.createConnectionChannel

  def createAckerConsumer(queueName: QueueName,
                          basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                          consumerArgs: Option[ConsumerArgs] = None)(
                           implicit channel: AMQPChannel): Stream[IO, (StreamAcker[IO], StreamConsumer[IO])] =
    consumingProgram.createAckerConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createAutoAckConsumer(
                             queueName: QueueName,
                             basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                             consumerArgs: Option[ConsumerArgs] = None)(implicit channel: AMQPChannel): Stream[IO, StreamConsumer[IO]] =
    consumingProgram.createAutoAckConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createPublisher(exchangeName: ExchangeName, routingKey: RoutingKey)(
    implicit channel: AMQPChannel): Stream[IO, StreamPublisher[IO]] =
    publishingProgram.createPublisher(channel.value, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.bindQueueNoWait(channel.value, queueName, exchangeName, routingKey, args)

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.unbindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.bindExchange(channel.value, destination, source, routingKey, args)

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.declareExchange(channel.value, exchangeName, exchangeType)

  def declareQueue(queueName: QueueName)(implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.declareQueue(channel.value, queueName)

  def deleteQueue(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.deleteQueue(channel.value, queueName, ifUnused, ifEmpty)

  def deleteQueueNoWait(queueName: QueueName, ifUnused: Boolean = true, ifEmpty: Boolean = true)(
    implicit channel: AMQPChannel): Stream[IO, Unit] =
    amqpClient.deleteQueueNoWait(channel.value, queueName, ifUnused, ifEmpty)

}
