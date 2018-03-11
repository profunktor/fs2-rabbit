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
import cats.syntax.apply._
import com.github.gvolpe.fs2rabbit.algebra.AMQPClient
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.config.deletion.DeletionQueueConfig
import com.github.gvolpe.fs2rabbit.model
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.rabbitmq.client.Channel
import fs2.Stream
import fs2.async.mutable

import scala.collection.mutable.{Set => MutableSet}

class AMQPClientInMemory(internalQ: mutable.Queue[IO, Either[Throwable, AmqpEnvelope]],
                         ackerQ: mutable.Queue[IO, AckResult],
                         config: Fs2RabbitConfig)
    extends AMQPClient[Stream[IO, ?]] {

  private val queues: MutableSet[QueueName] = MutableSet.empty[QueueName]

  override def basicAck(channel: Channel, tag: model.DeliveryTag, multiple: Boolean): Stream[IO, Unit] =
    Stream.eval(ackerQ.enqueue1(Ack(tag)))

  override def basicNack(channel: Channel,
                         tag: model.DeliveryTag,
                         multiple: Boolean,
                         requeue: Boolean): Stream[IO, Unit] = {
    // Imitating the RabbitMQ behavior
    val envelope = AmqpEnvelope(DeliveryTag(1), "requeued msg", AmqpProperties.empty)
    for {
      _ <- Stream.eval(ackerQ.enqueue1(NAck(tag)))
      _ <- if (config.requeueOnNack) Stream.eval(internalQ.enqueue1(Right(envelope))) else Stream.eval(IO.unit)
    } yield ()
  }

  override def basicQos(channel: Channel, basicQos: model.BasicQos): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def basicConsume(channel: Channel,
                            queueName: model.QueueName,
                            autoAck: Boolean,
                            consumerTag: String,
                            noLocal: Boolean,
                            exclusive: Boolean,
                            args: Map[String, AnyRef]): Stream[IO, String] = {
    val ifError =
      Stream.raiseError[String](new java.io.IOException(s"Queue ${queueName.value} does not exist!")).covary[IO]
    queues.find(_.value == queueName.value).fold(ifError) { _ =>
      Stream.eval(IO("dequeue1 happens in AckerConsumerProgram.createConsumer"))
    }
  }

  override def basicPublish(channel: Channel,
                            exchangeName: model.ExchangeName,
                            routingKey: model.RoutingKey,
                            msg: model.AmqpMessage[String]): Stream[IO, Unit] = {
    val envelope = AmqpEnvelope(DeliveryTag(1), msg.payload, msg.properties)
    Stream.eval(internalQ.enqueue1(Right(envelope)))
  }

  override def deleteQueue(channel: Channel, config: DeletionQueueConfig): Stream[IO, Unit] =
    Stream.eval(IO(queues -= config.queueName) *> IO.unit)

  override def deleteQueueNoWait(channel: Channel, config: DeletionQueueConfig): Stream[IO, Unit] =
    Stream.eval(IO(queues -= config.queueName) *> IO.unit)

  override def bindQueue(channel: Channel,
                         queueName: model.QueueName,
                         exchangeName: model.ExchangeName,
                         routingKey: model.RoutingKey): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def bindQueue(channel: Channel,
                         queueName: model.QueueName,
                         exchangeName: model.ExchangeName,
                         routingKey: model.RoutingKey,
                         args: model.QueueBindingArgs): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def bindQueueNoWait(channel: Channel,
                               queueName: model.QueueName,
                               exchangeName: model.ExchangeName,
                               routingKey: model.RoutingKey,
                               args: model.QueueBindingArgs): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def unbindQueue(channel: Channel,
                           queueName: model.QueueName,
                           exchangeName: model.ExchangeName,
                           routingKey: model.RoutingKey): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def bindExchange(channel: Channel,
                            destination: model.ExchangeName,
                            source: model.ExchangeName,
                            routingKey: model.RoutingKey,
                            args: model.ExchangeBindingArgs): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def declareExchange(channel: Channel,
                               exchangeName: model.ExchangeName,
                               exchangeType: ExchangeType): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def declareQueue(channel: Channel, queueConfig: DeclarationQueueConfig): Stream[IO, Unit] =
    Stream.eval(IO(queues += queueConfig.queueName) *> IO.unit)

  override def declareQueueNoWait(channel: Channel, queueConfig: DeclarationQueueConfig): Stream[IO, Unit] =
    Stream.eval(IO(queues += queueConfig.queueName) *> IO.unit)

  override def declareQueuePassive(channel: Channel, queueName: QueueName): Stream[IO, Unit] =
    Stream.eval(IO(queues += queueName) *> IO.unit)

}
