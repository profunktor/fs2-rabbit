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

import cats.Applicative
import cats.effect.{Effect, Sync}
import cats.effect.syntax.effect._
import cats.implicits._
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, AMQPInternals}
import com.github.gvolpe.fs2rabbit.arguments._
import com.github.gvolpe.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import com.github.gvolpe.fs2rabbit.config.deletion
import com.github.gvolpe.fs2rabbit.config.deletion.DeletionQueueConfig
import com.github.gvolpe.fs2rabbit.effects.BoolValue.syntax._
import com.github.gvolpe.fs2rabbit.effects.{EnvelopeDecoder, StreamEval}
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client._
import fs2.Stream

class AMQPClientStream[F[_]: Effect](implicit SE: StreamEval[F]) extends AMQPClient[Stream[F, ?], F] {

  private[fs2rabbit] def defaultConsumer[A](
      channel: Channel,
      internals: AMQPInternals[F, A]
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Consumer] = Applicative[F].pure {
    new DefaultConsumer(channel) {

      override def handleCancel(consumerTag: String): Unit =
        internals.queue.fold(()) { internalQ =>
          internalQ
            .enqueue1(Left(new Exception(s"Queue might have been DELETED! $consumerTag")))
            .toIO
            .unsafeRunAsync(_ => ())
        }

      override def handleDelivery(
          consumerTag: String,
          envelope: Envelope,
          properties: AMQP.BasicProperties,
          body: Array[Byte]
      ): Unit = {
        val tag   = envelope.getDeliveryTag
        val props = AmqpProperties.from(properties)
        internals.queue.fold(()) { internalQ =>
          val envelope = AmqpEnvelope(DeliveryTag(tag), body, props)
          decoder
            .run(envelope)
            .attempt
            .flatMap { msg => internalQ.enqueue1(msg.map(a => envelope.copy(payload = a)))
            }
            .toIO
            .unsafeRunAsync(_ => ())
        }
      }
    }
  }

  override def basicAck(
      channel: Channel,
      tag: DeliveryTag,
      multiple: Boolean
  ): F[Unit] = Sync[F].delay {
    channel.basicAck(tag.value, multiple)
  }

  override def basicNack(
      channel: Channel,
      tag: DeliveryTag,
      multiple: Boolean,
      requeue: Boolean
  ): F[Unit] =
    Sync[F].delay {
      channel.basicNack(tag.value, multiple, requeue)
    }

  override def basicQos(
      channel: Channel,
      basicQos: BasicQos
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.basicQos(basicQos.prefetchSize, basicQos.prefetchCount, basicQos.global)
  }

  override def basicConsume[A](
      channel: Channel,
      queueName: QueueName,
      autoAck: Boolean,
      consumerTag: ConsumerTag,
      noLocal: Boolean,
      exclusive: Boolean,
      args: Arguments
  )(internals: AMQPInternals[F, A])(implicit decoder: EnvelopeDecoder[F, A]): F[ConsumerTag] =
    for {
      dc <- defaultConsumer(channel, internals)
      rs <- Sync[F].delay(
             channel.basicConsume(queueName.value, autoAck, consumerTag.value, noLocal, exclusive, args, dc)
           )
    } yield ConsumerTag(rs)

  override def basicCancel(
      channel: Channel,
      consumerTag: ConsumerTag
  ): F[Unit] = Sync[F].delay {
    channel.basicCancel(consumerTag.value)
  }

  override def basicPublish(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      msg: AmqpMessage[String]
  ): F[Unit] = Sync[F].delay {
    channel.basicPublish(
      exchangeName.value,
      routingKey.value,
      msg.properties.asBasicProps,
      msg.payload.getBytes("UTF-8")
    )
  }

  def basicPublishWithFlag(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      msg: AmqpMessage[String]
  ): F[Unit] = Sync[F].delay {
    channel.basicPublish(
      exchangeName.value,
      routingKey.value,
      flag.mandatory,
      msg.properties.asBasicProps,
      msg.payload.getBytes("UTF-8")
    )
  }

  override def addPublishingListener(
      channel: Channel,
      listener: PublishReturn => F[Unit]
  ): Stream[F, Unit] = SE.evalDiscard {
    val returnListener = new ReturnListener {
      override def handleReturn(
          replyCode: Int,
          replyText: String,
          exchange: String,
          routingKey: String,
          properties: AMQP.BasicProperties,
          body: Array[Byte]
      ): Unit = {
        val publishReturn =
          PublishReturn(
            ReplyCode(replyCode),
            ReplyText(replyText),
            ExchangeName(exchange),
            RoutingKey(routingKey),
            AmqpProperties.from(properties),
            AmqpBody(new String(body, "UTF-8"))
          )

        listener(publishReturn).toIO.unsafeRunAsync(_ => ())
      }
    }

    channel.addReturnListener(returnListener)
  }

  override def clearPublishingListeners(channel: Channel): Stream[F, Unit] = SE.evalDiscard {
    channel.clearReturnListeners()
  }

  override def bindQueue(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueBind(queueName.value, exchangeName.value, routingKey.value)
  }

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey,
                         args: QueueBindingArgs): Stream[F, Unit] =
    SE.evalDiscard {
      channel.queueBind(queueName.value, exchangeName.value, routingKey.value, args.value)
    }

  override def bindQueueNoWait(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueBindingArgs
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueBindNoWait(queueName.value, exchangeName.value, routingKey.value, args.value)
  }

  override def unbindQueue(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  ): Stream[F, Unit] = SE.evalDiscard {
    unbindQueue(channel, queueName, exchangeName, routingKey, QueueUnbindArgs(Map.empty))
  }

  override def unbindQueue(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueUnbindArgs
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueUnbind(queueName.value, exchangeName.value, routingKey.value, args.value)
  }

  override def bindExchange(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeBind(destination.value, source.value, routingKey.value, args.value)
  }

  override def bindExchangeNoWait(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeBindNoWait(destination.value, source.value, routingKey.value, args.value)
  }

  override def unbindExchange(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeUnbindArgs
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeUnbind(destination.value, source.value, routingKey.value, args.value)
  }

  override def declareExchange(
      channel: Channel,
      config: DeclarationExchangeConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeDeclare(
      config.exchangeName.value,
      config.exchangeType.toString.toLowerCase,
      config.durable.isTrue,
      config.autoDelete.isTrue,
      config.internal.isTrue,
      config.arguments
    )
  }

  override def declareExchangeNoWait(
      channel: Channel,
      config: DeclarationExchangeConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeDeclareNoWait(
      config.exchangeName.value,
      config.exchangeType.toString.toLowerCase,
      config.durable.isTrue,
      config.autoDelete.isTrue,
      config.internal.isTrue,
      config.arguments
    )
  }

  override def declareExchangePassive(
      channel: Channel,
      exchangeName: ExchangeName
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeDeclarePassive(exchangeName.value)
  }

  override def declareQueue(
      channel: Channel,
      config: DeclarationQueueConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueDeclare(
      config.queueName.value,
      config.durable.isTrue,
      config.exclusive.isTrue,
      config.autoDelete.isTrue,
      config.arguments
    )
  }

  override def declareQueueNoWait(
      channel: Channel,
      config: DeclarationQueueConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueDeclareNoWait(
      config.queueName.value,
      config.durable.isTrue,
      config.exclusive.isTrue,
      config.autoDelete.isTrue,
      config.arguments
    )
  }

  override def declareQueuePassive(
      channel: Channel,
      queueName: QueueName
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueDeclarePassive(queueName.value)
  }

  override def deleteQueue(
      channel: Channel,
      config: DeletionQueueConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueDelete(config.queueName.value, config.ifUnused.isTrue, config.ifEmpty.isTrue)
  }

  override def deleteQueueNoWait(
      channel: Channel,
      config: DeletionQueueConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.queueDeleteNoWait(config.queueName.value, config.ifUnused.isTrue, config.ifEmpty.isTrue)
  }

  override def deleteExchange(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeDelete(config.exchangeName.value, config.ifUnused.isTrue)
  }

  override def deleteExchangeNoWait(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): Stream[F, Unit] = SE.evalDiscard {
    channel.exchangeDeleteNoWait(config.exchangeName.value, config.ifUnused.isTrue)
  }

}
