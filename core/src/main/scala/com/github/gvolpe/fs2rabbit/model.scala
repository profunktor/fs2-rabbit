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

package com.github.gvolpe.fs2rabbit

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8

import cats.ApplicativeError
import cats.data.Kleisli
import cats.implicits._
import com.github.gvolpe.fs2rabbit.arguments.Arguments
import com.github.gvolpe.fs2rabbit.effects.EnvelopeDecoder
import com.github.gvolpe.fs2rabbit.model.AmqpHeaderVal._
import com.rabbitmq.client.impl.LongStringHelper
import com.rabbitmq.client.{AMQP, Channel, LongString}
import fs2.Stream

import scala.collection.JavaConverters._

object model {

  type StreamAckerConsumer[F[_], A] = (AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])

  trait AMQPChannel {
    def value: Channel
  }
  case class RabbitChannel(value: Channel) extends AMQPChannel

  case class ExchangeName(value: String) extends AnyVal
  case class QueueName(value: String)    extends AnyVal
  case class RoutingKey(value: String)   extends AnyVal
  case class DeliveryTag(value: Long)    extends AnyVal
  case class ConsumerTag(value: String)  extends AnyVal

  case class ConsumerArgs(consumerTag: ConsumerTag, noLocal: Boolean, exclusive: Boolean, args: Arguments)
  case class BasicQos(prefetchSize: Int, prefetchCount: Int, global: Boolean = false)

  sealed trait ExchangeType extends Product with Serializable

  object ExchangeType {
    case object Direct  extends ExchangeType
    case object FanOut  extends ExchangeType
    case object Headers extends ExchangeType
    case object Topic   extends ExchangeType
  }

  sealed abstract class DeliveryMode(val value: Int) extends Product with Serializable

  object DeliveryMode {
    case object NonPersistent extends DeliveryMode(1)
    case object Persistent    extends DeliveryMode(2)

    def from(value: Int): DeliveryMode = value match {
      case 1 => NonPersistent
      case 2 => Persistent
    }
  }

  sealed trait AckResult extends Product with Serializable

  object AckResult {
    final case class Ack(deliveryTag: DeliveryTag)  extends AckResult
    final case class NAck(deliveryTag: DeliveryTag) extends AckResult
  }

  sealed trait AmqpHeaderVal extends Product with Serializable {
    def impure: AnyRef = this match {
      case StringVal(v) => LongStringHelper.asLongString(v)
      case IntVal(v)    => Int.box(v)
      case LongVal(v)   => Long.box(v)
      case ArrayVal(v)  => v.asJava
    }
  }

  object AmqpHeaderVal {
    final case class IntVal(value: Int)       extends AmqpHeaderVal
    final case class LongVal(value: Long)     extends AmqpHeaderVal
    final case class StringVal(value: String) extends AmqpHeaderVal
    final case class ArrayVal(v: Seq[Any])    extends AmqpHeaderVal

    def from(value: AnyRef): AmqpHeaderVal = value match {
      case ls: LongString       => StringVal(new String(ls.getBytes, "UTF-8"))
      case s: String            => StringVal(s)
      case l: java.lang.Long    => LongVal(l)
      case i: java.lang.Integer => IntVal(i)
      case a: java.util.List[_] => ArrayVal(a.asScala)
    }
  }

  case class AmqpProperties(
      contentType: Option[String] = None,
      contentEncoding: Option[String] = None,
      priority: Option[Int] = None,
      deliveryMode: Option[DeliveryMode] = None,
      correlationId: Option[String] = None,
      messageId: Option[String] = None,
      `type`: Option[String] = None,
      userId: Option[String] = None,
      appId: Option[String] = None,
      expiration: Option[String] = None,
      headers: Map[String, AmqpHeaderVal] = Map.empty
  )

  object AmqpProperties {
    def empty = AmqpProperties()

    def from(basicProps: AMQP.BasicProperties): AmqpProperties =
      AmqpProperties(
        contentType = Option(basicProps.getContentType),
        contentEncoding = Option(basicProps.getContentEncoding),
        priority = Option[Integer](basicProps.getPriority).map(Int.unbox),
        deliveryMode = Option(basicProps.getDeliveryMode).map(DeliveryMode.from(_)),
        correlationId = Option(basicProps.getCorrelationId),
        messageId = Option(basicProps.getMessageId),
        `type` = Option(basicProps.getType),
        userId = Option(basicProps.getUserId),
        appId = Option(basicProps.getAppId),
        expiration = Option(basicProps.getExpiration),
        headers = Option(basicProps.getHeaders)
          .fold(Map.empty[String, Object])(_.asScala.toMap)
          .map {
            case (k, v) => k -> AmqpHeaderVal.from(v)
          }
      )

    implicit class AmqpPropertiesOps(props: AmqpProperties) {
      def asBasicProps: AMQP.BasicProperties =
        new AMQP.BasicProperties.Builder()
          .contentType(props.contentType.orNull)
          .contentEncoding(props.contentEncoding.orNull)
          .priority(props.priority.map(Int.box).orNull)
          .deliveryMode(props.deliveryMode.map(i => Int.box(i.value)).orNull)
          .correlationId(props.correlationId.orNull)
          .messageId(props.messageId.orNull)
          .`type`(props.`type`.orNull)
          .appId(props.appId.orNull)
          .userId(props.userId.orNull)
          .expiration(props.expiration.orNull)
          .headers(props.headers.mapValues[AnyRef](_.impure).asJava)
          .build()
    }
  }

  case class AmqpEnvelope[A](deliveryTag: DeliveryTag, payload: A, properties: AmqpProperties)
  case class AmqpMessage[A](payload: A, properties: AmqpProperties)

  object AmqpEnvelope {
    private def encoding[F[_]](implicit F: ApplicativeError[F, Throwable]): EnvelopeDecoder[F, Option[Charset]] =
      Kleisli(_.properties.contentEncoding.traverse(n => F.catchNonFatal(Charset.forName(n))))

    // usually this would go in the EnvelopeDecoder companion object, but since that's only a type alias,
    // we need to put it here for the compiler to find it during implicit search
    implicit def stringDecoder[F[_]: ApplicativeError[?[_], Throwable]]: EnvelopeDecoder[F, String] =
      (EnvelopeDecoder.payload[F], encoding[F]).mapN((p, e) => new String(p, e.getOrElse(UTF_8)))

  }

  // Binding
  case class QueueBindingArgs(value: Arguments)    extends AnyVal
  case class ExchangeBindingArgs(value: Arguments) extends AnyVal

  // Unbind
  case class QueueUnbindArgs(value: Arguments)    extends AnyVal
  case class ExchangeUnbindArgs(value: Arguments) extends AnyVal

  // Declaration
  case class QueueDeclarationArgs(value: Arguments)    extends AnyVal
  case class ExchangeDeclarationArgs(value: Arguments) extends AnyVal

  // Publishing
  case class ReplyCode(value: Int)    extends AnyVal
  case class ReplyText(value: String) extends AnyVal
  case class AmqpBody(value: String)  extends AnyVal

  case class PublishReturn(
      replyCode: ReplyCode,
      replyText: ReplyText,
      exchange: ExchangeName,
      routingKey: RoutingKey,
      properties: AmqpProperties,
      body: AmqpBody
  )

  case class PublishingFlag(mandatory: Boolean) extends AnyVal

}
