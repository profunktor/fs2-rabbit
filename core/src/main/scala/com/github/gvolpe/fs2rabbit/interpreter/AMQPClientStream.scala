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

import cats.effect.Effect
import cats.effect.syntax.effect._
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, AMQPInternals}
import com.github.gvolpe.fs2rabbit.arguments._
import com.github.gvolpe.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import com.github.gvolpe.fs2rabbit.config.deletion
import com.github.gvolpe.fs2rabbit.config.deletion.DeletionQueueConfig
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.util.BoolValue.syntax._
import com.github.gvolpe.fs2rabbit.util.StreamEval
import com.rabbitmq.client._
import fs2.Stream

class AMQPClientStream[F[_]: Effect](implicit SE: StreamEval[F]) extends AMQPClient[Stream[F, ?], F] {

  private[fs2rabbit] def defaultConsumer(channel: Channel, internals: AMQPInternals[F]): Stream[F, Consumer] =
    SE.pure(
      new DefaultConsumer(channel) {

        override def handleCancel(consumerTag: String): Unit =
          internals.queue.fold(()) { internalQ =>
            internalQ
              .enqueue1(Left(new Exception(s"Queue might have been DELETED! $consumerTag")))
              .toIO
              .unsafeRunAsync(_ => ())
          }

        override def handleDelivery(consumerTag: String,
                                    envelope: Envelope,
                                    properties: AMQP.BasicProperties,
                                    body: Array[Byte]): Unit = {
          val msg   = new String(body, "UTF-8")
          val tag   = envelope.getDeliveryTag
          val props = AmqpProperties.from(properties)
          internals.queue.fold(()) { internalQ =>
            internalQ
              .enqueue1(Right(AmqpEnvelope(DeliveryTag(tag), msg, props)))
              .toIO
              .unsafeRunAsync(_ => ())
          }
        }
      }
    )

  override def basicAck(channel: Channel, tag: DeliveryTag, multiple: Boolean): Stream[F, Unit] = SE.evalF {
    channel.basicAck(tag.value, multiple)
  }

  override def basicNack(channel: Channel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): Stream[F, Unit] =
    SE.evalF {
      channel.basicNack(tag.value, multiple, requeue)
    }

  override def basicQos(channel: Channel, basicQos: BasicQos): Stream[F, Unit] = SE.evalF {
    channel.basicQos(basicQos.prefetchSize, basicQos.prefetchCount, basicQos.global)
  }

  override def basicConsume(channel: Channel,
                            queueName: QueueName,
                            autoAck: Boolean,
                            consumerTag: String,
                            noLocal: Boolean,
                            exclusive: Boolean,
                            args: Arguments)(internals: AMQPInternals[F]): Stream[F, String] =
    for {
      dc <- defaultConsumer(channel, internals)
      rs <- SE.evalF(channel.basicConsume(queueName.value, autoAck, consumerTag, noLocal, exclusive, args, dc))
    } yield rs

  override def basicPublish(channel: Channel,
                            exchangeName: ExchangeName,
                            routingKey: RoutingKey,
                            msg: AmqpMessage[String]): Stream[F, Unit] = SE.evalF {
    channel.basicPublish(exchangeName.value,
                         routingKey.value,
                         msg.properties.asBasicProps,
                         msg.payload.getBytes("UTF-8"))
  }

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey): Stream[F, Unit] = SE.evalF {
    channel.queueBind(queueName.value, exchangeName.value, routingKey.value)
  }

  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey,
                         args: QueueBindingArgs): Stream[F, Unit] = SE.evalF {
    channel.queueBind(queueName.value, exchangeName.value, routingKey.value, args.value)
  }

  override def bindQueueNoWait(channel: Channel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey,
                               args: QueueBindingArgs): Stream[F, Unit] = SE.evalF {
    channel.queueBindNoWait(queueName.value, exchangeName.value, routingKey.value, args.value)
  }

  override def unbindQueue(channel: Channel,
                           queueName: QueueName,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey): Stream[F, Unit] = SE.evalF {
    channel.queueUnbind(queueName.value, exchangeName.value, routingKey.value)
  }

  override def bindExchange(channel: Channel,
                            destination: ExchangeName,
                            source: ExchangeName,
                            routingKey: RoutingKey,
                            args: ExchangeBindingArgs): Stream[F, Unit] = SE.evalF {
    channel.exchangeBind(destination.value, source.value, routingKey.value, args.value)
  }

  override def declareExchange(channel: Channel, config: DeclarationExchangeConfig): Stream[F, Unit] = SE.evalF {
    channel.exchangeDeclare(
      config.exchangeName.value,
      config.exchangeType.toString.toLowerCase,
      config.durable.isTrue,
      config.autoDelete.isTrue,
      config.internal.isTrue,
      config.arguments
    )
  }

  override def declareExchangePassive(channel: Channel, exchangeName: ExchangeName): Stream[F, Unit] = SE.evalF {
    channel.exchangeDeclarePassive(exchangeName.value)
  }

  override def declareQueue(channel: Channel, config: DeclarationQueueConfig): Stream[F, Unit] = SE.evalF {
    channel.queueDeclare(
      config.queueName.value,
      config.durable.isTrue,
      config.exclusive.isTrue,
      config.autoDelete.isTrue,
      config.arguments
    )
  }

  override def declareQueueNoWait(channel: Channel, config: DeclarationQueueConfig): Stream[F, Unit] =
    SE.evalF {
      channel.queueDeclareNoWait(
        config.queueName.value,
        config.durable.isTrue,
        config.exclusive.isTrue,
        config.autoDelete.isTrue,
        config.arguments
      )
    }

  override def declareQueuePassive(channel: Channel, queueName: QueueName): Stream[F, Unit] = SE.evalF {
    channel.queueDeclarePassive(queueName.value)
  }

  override def deleteQueue(channel: Channel, config: DeletionQueueConfig): Stream[F, Unit] = SE.evalF {
    channel.queueDelete(config.queueName.value, config.ifUnused.isTrue, config.ifEmpty.isTrue)
  }

  override def deleteQueueNoWait(channel: Channel, config: DeletionQueueConfig): Stream[F, Unit] = SE.evalF {
    channel.queueDeleteNoWait(config.queueName.value, config.ifUnused.isTrue, config.ifEmpty.isTrue)
  }

  override def deleteExchange(channel: Channel, config: deletion.DeletionExchangeConfig): Stream[F, Unit] = SE.evalF {
    channel.exchangeDelete(config.exchangeName.value, config.ifUnused.isTrue)
  }

  override def deleteExchangeNoWait(channel: Channel, config: deletion.DeletionExchangeConfig): Stream[F, Unit] =
    SE.evalF {
      channel.exchangeDeleteNoWait(config.exchangeName.value, config.ifUnused.isTrue)
    }

}
