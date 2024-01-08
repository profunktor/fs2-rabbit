/*
 * Copyright 2017-2024 ProfunKtor
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

package dev.profunktor.fs2rabbit.algebra

import cats.effect.kernel.Sync
import cats.effect.std.Dispatcher
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Functor}
import com.rabbitmq.client.{AMQP, Consumer, DefaultConsumer, Envelope, ShutdownSignalException}
import dev.profunktor.fs2rabbit.arguments.{Arguments, _}
import dev.profunktor.fs2rabbit.model._

import scala.util.{Failure, Success, Try}

object Consume {
  def make[F[_]: Sync](dispatcher: Dispatcher[F]): Consume[F] =
    new Consume[F] {
      private[fs2rabbit] def defaultConsumer[A](
          channel: AMQPChannel,
          internals: AMQPInternals[F]
      ): F[Consumer] = Applicative[F].pure {
        new DefaultConsumer(channel.value) {

          override def handleCancel(consumerTag: String): Unit =
            internals.queue.fold(()) { internalQ =>
              dispatcher.unsafeRunAndForget {
                internalQ
                  .offer(
                    Left(
                      new Exception(
                        s"Queue might have been DELETED! $consumerTag"
                      )
                    )
                  )
              }
            }

          override def handleDelivery(
              consumerTag: String,
              envelope: Envelope,
              properties: AMQP.BasicProperties,
              body: Array[Byte]
          ): Unit = {
            def rewrappedError(err: Throwable) =
              Left(
                new Exception(
                  s"""
                You've stumbled across a bug in the interface between the underlying
                RabbitMQ Java library and fs2-rabbit! Please report this bug and
                include this stack trace and message.\n

                The BasicProperties instance that caused this error was:\n

                $properties
                """,
                  err
                )
              )

            val amqpPropertiesOrErr =
              Try(AmqpProperties.unsafeFrom(properties)) match {
                case Success(p) => Right(p)
                case Failure(t) => rewrappedError(t)
              }

            val tag         = envelope.getDeliveryTag
            val routingKey  = RoutingKey(envelope.getRoutingKey)
            val exchange    = ExchangeName(envelope.getExchange)
            val redelivered = envelope.isRedeliver

            // Calling the Functor instance manually for compatibility
            import cats.instances.either._

            val envelopeOrErr =
              Functor[Either[Throwable, *]].map(amqpPropertiesOrErr) { props =>
                AmqpEnvelope(
                  DeliveryTag(tag),
                  body,
                  props,
                  exchange,
                  routingKey,
                  redelivered
                )
              }

            // Block on offering thus preserving order of messages
            dispatcher.unsafeRunSync {
              internals.queue
                .fold(Applicative[F].unit) { internalQ =>
                  internalQ.offer(envelopeOrErr)
                }
            }
          }

          override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
            if (!sig.isInitiatedByApplication) {
              internals.queue.foreach(q => dispatcher.unsafeRunAndForget(q.offer(Left(sig))))
            }
        }
      }

      override def basicAck(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean): F[Unit] = Sync[F].blocking {
        channel.value.basicAck(tag.value, multiple)
      }

      override def basicNack(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
        Sync[F].blocking {
          channel.value.basicNack(tag.value, multiple, requeue)
        }

      override def basicReject(channel: AMQPChannel, tag: DeliveryTag, requeue: Boolean): F[Unit] = Sync[F].blocking {
        channel.value.basicReject(tag.value, requeue)
      }

      override def basicQos(channel: AMQPChannel, basicQos: BasicQos): F[Unit] =
        Sync[F].blocking {
          channel.value.basicQos(
            basicQos.prefetchSize,
            basicQos.prefetchCount,
            basicQos.global
          )
        }.void

      override def basicConsume[A](
          channel: AMQPChannel,
          queueName: QueueName,
          autoAck: Boolean,
          consumerTag: ConsumerTag,
          noLocal: Boolean,
          exclusive: Boolean,
          args: Arguments
      )(internals: AMQPInternals[F]): F[ConsumerTag] =
        for {
          dc <- defaultConsumer(channel, internals)
          rs <- Sync[F].blocking(
                  channel.value.basicConsume(
                    queueName.value,
                    autoAck,
                    consumerTag.value,
                    noLocal,
                    exclusive,
                    args,
                    dc
                  )
                )
        } yield ConsumerTag(rs)

      override def basicCancel(channel: AMQPChannel, consumerTag: ConsumerTag): F[Unit] =
        Sync[F].blocking {
          channel.value.basicCancel(consumerTag.value)
        }
    }
}

trait Consume[F[_]] extends Cancel[F] {
  def basicAck(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean): F[Unit]
  def basicNack(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]
  def basicReject(channel: AMQPChannel, tag: DeliveryTag, requeue: Boolean): F[Unit]
  def basicQos(channel: AMQPChannel, basicQos: BasicQos): F[Unit]
  def basicConsume[A](
      channel: AMQPChannel,
      queueName: QueueName,
      autoAck: Boolean,
      consumerTag: ConsumerTag,
      noLocal: Boolean,
      exclusive: Boolean,
      args: Arguments
  )(internals: AMQPInternals[F]): F[ConsumerTag]
}
