/*
 * Copyright 2017-2023 ProfunKtor
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

import cats.effect.Sync
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.option._
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Envelope
import dev.profunktor.fs2rabbit.model._

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Get {
  def make[F[_]: Sync]: Get[F] = new Get[F] {
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

    private def toEnvelope(
        envelope: Envelope,
        properties: AMQP.BasicProperties,
        body: Array[Byte]
    ): Either[Throwable, AmqpEnvelope[Array[Byte]]] = {
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

      amqpPropertiesOrErr.map { props =>
        AmqpEnvelope(
          DeliveryTag(tag),
          body,
          props,
          exchange,
          routingKey,
          redelivered
        )
      }
    }

    override def basicGet(
        channel: AMQPChannel,
        queue: QueueName,
        autoAck: Boolean
    ): F[Either[Throwable, Option[AmqpEnvelope[Array[Byte]]]]] = Sync[F]
      .blocking {
        val resp = channel.value.basicGet(queue.value, autoAck)
        if (resp != null) Some(resp) else None
      }
      .map {
        case None       => none[AmqpEnvelope[Array[Byte]]].asRight[Throwable]
        case Some(resp) => toEnvelope(resp.getEnvelope, resp.getProps, resp.getBody).map(_.some)
      }
  }
}

trait Get[F[_]] {
  def basicAck(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean): F[Unit]
  def basicNack(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]
  def basicReject(channel: AMQPChannel, tag: DeliveryTag, requeue: Boolean): F[Unit]
  def basicGet(
      channel: AMQPChannel,
      queue: QueueName,
      autoAck: Boolean
  ): F[Either[Throwable, Option[AmqpEnvelope[Array[Byte]]]]]
}
