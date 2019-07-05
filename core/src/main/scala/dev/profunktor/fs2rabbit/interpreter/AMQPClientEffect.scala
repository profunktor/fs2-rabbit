/*
 * Copyright 2017-2019 ProfunKtor
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

import cats.Applicative
import cats.effect.syntax.effect._
import cats.effect.{Effect, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.rabbitmq.client._
import dev.profunktor.fs2rabbit.algebra.{AMQPClient, AMQPInternals}
import dev.profunktor.fs2rabbit.arguments._
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.config.deletion
import dev.profunktor.fs2rabbit.config.deletion.DeletionQueueConfig
import dev.profunktor.fs2rabbit.effects.BoolValue.syntax._
import dev.profunktor.fs2rabbit.model._

class AmqpClientEffect[F[_]: Effect] extends AMQPClient[F] {

  private[fs2rabbit] def defaultConsumer[A](
      channel: Channel,
      internals: AMQPInternals[F]
  ): F[Consumer] = Sync[F].delay {
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
        // This should not go wrong (if it does it is an indication of a bug in
        // unsafeFrom)!
        // However, I'm not entirely confident I've nailed down unsafeFrom (
        // since it requires a pretty intricate understanding of the underlying
        // Java library) so just in case, we're wrapping it in a Try so that a
        // bug here doesn't bring down our entire queue.
        val envelopeOrErr = (scala.util.Try(AmqpProperties.unsafeFrom(properties)) match {
          // toEither is not supported by Scala 2.11
          case scala.util.Success(amqpEnvelope) => Right(amqpEnvelope)
          case scala.util.Failure(err)          =>
            val rewrappedError = new Exception(
              "You've stumbled across a bug in the interface between the underlying " +
                "RabbitMQ Java library and fs2-rabbit! Please report this bug and " +
                "include this stack trace and message.\nThe BasicProperties instance " +
                s"that caused this error was:\n$properties\n",
              err
            )
            Left(rewrappedError)
        }).map{ props =>
          val tag         = envelope.getDeliveryTag
          val routingKey  = RoutingKey(envelope.getRoutingKey)
          val exchange    = ExchangeName(envelope.getExchange)
          val redelivered = envelope.isRedeliver
          AmqpEnvelope(DeliveryTag(tag), body, props, exchange, routingKey, redelivered)
        }
        internals.queue
          .fold(Applicative[F].pure(())) { internalQ =>
            internalQ.enqueue1(envelopeOrErr)
          }
          .toIO
          .unsafeRunAsync(_ => ())
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
  ): F[Unit] =
    Sync[F].delay {
      channel.basicQos(basicQos.prefetchSize, basicQos.prefetchCount, basicQos.global)
    }.void

  override def basicConsume[A](
      channel: Channel,
      queueName: QueueName,
      autoAck: Boolean,
      consumerTag: ConsumerTag,
      noLocal: Boolean,
      exclusive: Boolean,
      args: Arguments
  )(internals: AMQPInternals[F]): F[ConsumerTag] =
    for {
      dc <- defaultConsumer(channel, internals)
      rs <- Sync[F].delay(
             channel.basicConsume(queueName.value, autoAck, consumerTag.value, noLocal, exclusive, args, dc)
           )
    } yield ConsumerTag(rs)

  override def basicCancel(
      channel: Channel,
      consumerTag: ConsumerTag
  ): F[Unit] =
    Sync[F].delay {
      channel.basicCancel(consumerTag.value)
    }

  override def basicPublish(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      msg: AmqpMessage[Array[Byte]]
  ): F[Unit] = Sync[F].delay {
    channel.basicPublish(
      exchangeName.value,
      routingKey.value,
      msg.properties.asBasicProps,
      msg.payload
    )
  }

  override def basicPublishWithFlag(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      msg: AmqpMessage[Array[Byte]]
  ): F[Unit] = Sync[F].delay {
    channel.basicPublish(
      exchangeName.value,
      routingKey.value,
      flag.mandatory,
      msg.properties.asBasicProps,
      msg.payload
    )
  }

  override def addPublishingListener(
      channel: Channel,
      listener: PublishReturn => F[Unit]
  ): F[Unit] =
    Sync[F].delay {
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
              AmqpProperties.unsafeFrom(properties),
              AmqpBody(body)
            )

          listener(publishReturn).toIO.unsafeRunAsync(_ => ())
        }
      }

      channel.addReturnListener(returnListener)
    }.void

  override def clearPublishingListeners(channel: Channel): F[Unit] =
    Sync[F].delay {
      channel.clearReturnListeners()
    }.void

  override def bindQueue(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  ): F[Unit] =
    Sync[F].delay {
      channel.queueBind(queueName.value, exchangeName.value, routingKey.value)
    }.void

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey,
                         args: QueueBindingArgs): F[Unit] =
    Sync[F].delay {
      channel.queueBind(queueName.value, exchangeName.value, routingKey.value, args.value)
    }.void

  override def bindQueueNoWait(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueBindingArgs
  ): F[Unit] =
    Sync[F].delay {
      channel.queueBindNoWait(queueName.value, exchangeName.value, routingKey.value, args.value)
    }.void

  override def unbindQueue(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  ): F[Unit] =
    Sync[F].delay {
      unbindQueue(channel, queueName, exchangeName, routingKey, QueueUnbindArgs(Map.empty))
    }.void

  override def unbindQueue(
      channel: Channel,
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueUnbindArgs
  ): F[Unit] =
    Sync[F].delay {
      channel.queueUnbind(queueName.value, exchangeName.value, routingKey.value, args.value)
    }.void

  override def bindExchange(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeBind(destination.value, source.value, routingKey.value, args.value)
    }.void

  override def bindExchangeNoWait(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeBindNoWait(destination.value, source.value, routingKey.value, args.value)
    }.void

  override def unbindExchange(
      channel: Channel,
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeUnbindArgs
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeUnbind(destination.value, source.value, routingKey.value, args.value)
    }.void

  override def declareExchange(
      channel: Channel,
      config: DeclarationExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeclare(
        config.exchangeName.value,
        config.exchangeType.toString.toLowerCase,
        config.durable.isTrue,
        config.autoDelete.isTrue,
        config.internal.isTrue,
        config.arguments
      )
    }.void

  override def declareExchangeNoWait(
      channel: Channel,
      config: DeclarationExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeclareNoWait(
        config.exchangeName.value,
        config.exchangeType.toString.toLowerCase,
        config.durable.isTrue,
        config.autoDelete.isTrue,
        config.internal.isTrue,
        config.arguments
      )
    }.void

  override def declareExchangePassive(
      channel: Channel,
      exchangeName: ExchangeName
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeclarePassive(exchangeName.value)
    }.void

  override def declareQueue(channel: Channel): F[QueueName] =
    Sync[F].delay {
      QueueName(channel.queueDeclare().getQueue)
    }

  override def declareQueue(
      channel: Channel,
      config: DeclarationQueueConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.queueDeclare(
        config.queueName.value,
        config.durable.isTrue,
        config.exclusive.isTrue,
        config.autoDelete.isTrue,
        config.arguments
      )
    }.void

  override def declareQueueNoWait(
      channel: Channel,
      config: DeclarationQueueConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.queueDeclareNoWait(
        config.queueName.value,
        config.durable.isTrue,
        config.exclusive.isTrue,
        config.autoDelete.isTrue,
        config.arguments
      )
    }.void

  override def declareQueuePassive(
      channel: Channel,
      queueName: QueueName
  ): F[Unit] =
    Sync[F].delay {
      channel.queueDeclarePassive(queueName.value)
    }.void

  override def deleteQueue(
      channel: Channel,
      config: DeletionQueueConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.queueDelete(config.queueName.value, config.ifUnused.isTrue, config.ifEmpty.isTrue)
    }.void

  override def deleteQueueNoWait(
      channel: Channel,
      config: DeletionQueueConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.queueDeleteNoWait(config.queueName.value, config.ifUnused.isTrue, config.ifEmpty.isTrue)
    }.void

  override def deleteExchange(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDelete(config.exchangeName.value, config.ifUnused.isTrue)
    }.void

  override def deleteExchangeNoWait(
      channel: Channel,
      config: deletion.DeletionExchangeConfig
  ): F[Unit] =
    Sync[F].delay {
      channel.exchangeDeleteNoWait(config.exchangeName.value, config.ifUnused.isTrue)
    }.void

}
