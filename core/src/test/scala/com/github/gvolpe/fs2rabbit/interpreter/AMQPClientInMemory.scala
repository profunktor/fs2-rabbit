/*
 * Copyright 2017-2019 Fs2 Rabbit
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

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, AQMPInternals}
import com.github.gvolpe.fs2rabbit.arguments.Arguments
import com.github.gvolpe.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import com.github.gvolpe.fs2rabbit.config.deletion.DeletionQueueConfig
import com.github.gvolpe.fs2rabbit.config.{Fs2RabbitConfig, deletion}
import com.github.gvolpe.fs2rabbit.model
import com.github.gvolpe.fs2rabbit.model.AckResult.{Ack, NAck}
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel
import fs2.concurrent.Queue
import java.nio.charset.StandardCharsets.UTF_8

class AmqpClientInMemory(
    queues: Ref[IO, Set[QueueName]],
    exchanges: Ref[IO, Set[ExchangeName]],
    binds: Ref[IO, Map[String, ExchangeName]],
    ref: Ref[IO, AQMPInternals[IO]],
    consumers: Ref[IO, Set[ConsumerTag]],
    publishingQ: Queue[IO, Either[Throwable, AmqpEnvelope[Array[Byte]]]],
    listenerQ: Queue[IO, PublishReturn],
    ackerQ: Queue[IO, AckResult],
    config: Fs2RabbitConfig
)(implicit cs: ContextShift[IO])
    extends AMQPClient[IO] {

  private def raiseError[A](message: String): IO[A] =
    IO.raiseError(new java.io.IOException(message))

  override def basicAck(channel: Channel, tag: model.DeliveryTag, multiple: Boolean): IO[Unit] =
    ackerQ.enqueue1(Ack(tag))

  override def basicNack(
      channel: Channel,
      tag: model.DeliveryTag,
      multiple: Boolean,
      requeue: Boolean
  ): IO[Unit] = {
    // Imitating the RabbitMQ behavior
    val envelope = AmqpEnvelope(DeliveryTag(1),
                                "requeued msg".getBytes(UTF_8),
                                AmqpProperties.empty,
                                ExchangeName("test"),
                                RoutingKey("test.route"),
                                false)
    for {
      _ <- ackerQ.enqueue1(NAck(tag))
      _ <- if (config.requeueOnNack) publishingQ.enqueue1(Right(envelope))
          else IO.unit
    } yield ()
  }

  override def basicQos(
      channel: Channel,
      basicQos: model.BasicQos
  ): IO[Unit] = IO.unit

  override def basicConsume[A](
      channel: Channel,
      queueName: model.QueueName,
      autoAck: Boolean,
      consumerTag: ConsumerTag,
      noLocal: Boolean,
      exclusive: Boolean,
      args: Arguments
  )(internals: AQMPInternals[IO]): IO[ConsumerTag] = {
    val ifMissing =
      new java.io.IOException(s"Queue ${queueName.value} does not exist!")

    val tag =
      if (consumerTag.value.isEmpty) ConsumerTag("consumer-" + scala.util.Random.alphanumeric.take(5).mkString(""))
      else consumerTag

    for {
      q <- queues.get
      _ <- IO.fromEither(q.find(_.value == queueName.value).toRight(ifMissing))
      _ <- ref.set(internals)
      _ <- consumers.update(_ + tag)
      _ <- if (autoAck) ackerQ.enqueue1(Ack(DeliveryTag(42))) else IO.unit
    } yield tag
  }

  override def basicCancel(
      channel: Channel,
      consumerTag: ConsumerTag
  ): IO[Unit] = {
    def ifMissing = new java.io.IOException(s"ConsumerTag ${consumerTag.value} does not exist!")

    for {
      c <- consumers.get
      _ <- IO.fromEither(c.find(_ == consumerTag).toRight(ifMissing))
      _ <- consumers.update(_ - consumerTag)
    } yield ()
  }

  override def basicPublish(
      channel: Channel,
      exchangeName: model.ExchangeName,
      routingKey: model.RoutingKey,
      msg: model.AmqpMessage[Array[Byte]]
  ): IO[Unit] = {
    val envelope = AmqpEnvelope(DeliveryTag(1), msg.payload, msg.properties, exchangeName, routingKey, false)
    publishingQ.enqueue1(Right(envelope))
  }

  override def basicPublishWithFlag(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      msg: AmqpMessage[Array[Byte]]
  ): IO[Unit] = {
    val ifNoBind = {
      val publishReturn =
        PublishReturn(
          ReplyCode(123),
          ReplyText("test"),
          exchangeName,
          routingKey,
          msg.properties,
          AmqpBody(msg.payload)
        )
      listenerQ.enqueue1(publishReturn)
    }

    binds.get.flatMap(_.get(routingKey.value).fold(ifNoBind) { _ =>
      basicPublish(channel, exchangeName, routingKey, msg)
    })
  }

  override def addPublishingListener(
      channel: Channel,
      listener: PublishReturn => IO[Unit]
  ): IO[Unit] =
    listenerQ.dequeue1.flatMap(listener).start.void

  override def clearPublishingListeners(
      channel: Channel
  ): IO[Unit] = IO.unit

  override def deleteQueue(
      channel: Channel,
      config: DeletionQueueConfig
  ): IO[Unit] =
    queues.update(_ - config.queueName)

  override def deleteQueueNoWait(
      channel: Channel,
      config: DeletionQueueConfig
  ): IO[Unit] =
    deleteQueue(channel, config)

  override def deleteExchange(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): IO[Unit] = exchanges.get.flatMap(
    _.find(_ == config.exchangeName).fold(raiseError[Unit](s"Exchange ${config.exchangeName} does not exist")) {
      exchange =>
        exchanges.update(_ - exchange)
    }
  )

  override def deleteExchangeNoWait(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): IO[Unit] =
    deleteExchange(channel, config)

  override def bindQueue(
      channel: Channel,
      queueName: model.QueueName,
      exchangeName: model.ExchangeName,
      routingKey: model.RoutingKey
  ): IO[Unit] =
    binds.update(_.updated(routingKey.value, exchangeName))

  override def bindQueue(
      channel: Channel,
      queueName: model.QueueName,
      exchangeName: model.ExchangeName,
      routingKey: model.RoutingKey,
      args: model.QueueBindingArgs
  ): IO[Unit] =
    bindQueue(channel, queueName, exchangeName, routingKey)

  override def bindQueueNoWait(
      channel: Channel,
      queueName: model.QueueName,
      exchangeName: model.ExchangeName,
      routingKey: model.RoutingKey,
      args: model.QueueBindingArgs
  ): IO[Unit] =
    bindQueue(channel, queueName, exchangeName, routingKey)

  override def unbindQueue(
      channel: Channel,
      queueName: model.QueueName,
      exchangeName: model.ExchangeName,
      routingKey: model.RoutingKey
  ): IO[Unit] =
    binds.update(_ - routingKey.value)

  override def unbindQueue(
      channel: Channel,
      queueName: model.QueueName,
      exchangeName: model.ExchangeName,
      routingKey: model.RoutingKey,
      args: QueueUnbindArgs
  ): IO[Unit] =
    unbindQueue(channel, queueName, exchangeName, routingKey)

  override def bindExchange(
      channel: Channel,
      destination: model.ExchangeName,
      source: model.ExchangeName,
      routingKey: model.RoutingKey,
      args: model.ExchangeBindingArgs
  ): IO[Unit] = IO.unit

  override def bindExchangeNoWait(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  ): IO[Unit] = IO.unit

  override def unbindExchange(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeUnbindArgs
  ): IO[Unit] = IO.unit

  override def declareExchange(
      channel: Channel,
      exchangeConfig: DeclarationExchangeConfig
  ): IO[Unit] =
    declareExchangePassive(channel, exchangeConfig.exchangeName)

  override def declareExchangeNoWait(
      channel: Channel,
      exchangeConfig: DeclarationExchangeConfig
  ): IO[Unit] =
    declareExchangePassive(channel, exchangeConfig.exchangeName)

  override def declareExchangePassive(
      channel: Channel,
      exchangeName: ExchangeName
  ): IO[Unit] =
    exchanges.update(_ + exchangeName)

  override def declareQueue(
      channel: Channel,
      queueConfig: DeclarationQueueConfig
  ): IO[Unit] =
    declareQueuePassive(channel, queueConfig.queueName)

  override def declareQueueNoWait(
      channel: Channel,
      queueConfig: DeclarationQueueConfig
  ): IO[Unit] =
    declareQueuePassive(channel, queueConfig.queueName)

  override def declareQueuePassive(
      channel: Channel,
      queueName: QueueName
  ): IO[Unit] =
    queues.update(_ + queueName)

}
