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

package dev.profunktor.fs2rabbit

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Date

import cats.{Applicative, ApplicativeError}
import cats.data.Kleisli
import cats.implicits._
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import dev.profunktor.fs2rabbit.model.AmqpHeaderVal._
import dev.profunktor.fs2rabbit.javaConversion._
import com.rabbitmq.client.{AMQP, Channel, Connection, LongString}
import fs2.Stream

object model {

  type StreamAckerConsumer[F[_], A] = (AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])

  trait AMQPChannel {
    def value: Channel
  }
  case class RabbitChannel(value: Channel) extends AMQPChannel

  trait AMQPConnection {
    def value: Connection
  }
  case class RabbitConnection(value: Connection) extends AMQPConnection

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

    /**
      * The opposite of [[AmqpHeaderVal.from]]. Turns an [[AmqpHeaderVal]] into something that can be processed by [[com.rabbitmq.client.impl.ValueWriter]]
      */
    def impure: AnyRef = this match {
      case BigDecimalVal(v) => v.bigDecimal
      case DateVal(v)       => v
      case TableVal(v)      => v.asJava
      case ByteVal(v)       => Byte.box(v)
      case DoubleVal(v)     => Double.box(v)
      case FloatVal(v)      => Float.box(v)
      case ShortVal(v)      => Short.box(v)
      case ByteArrayVal(v)  => v
      case BooleanVal(v)    => Boolean.box(v)
      case IntVal(v)        => Int.box(v)
      case LongVal(v)       => Long.box(v)
      case StringVal(v)     => v
      case LongStringVal(v) => v
      case ArrayVal(v)      => v.asJava
      case NullVal          => null
    }
  }

  object AmqpHeaderVal {
    // This hierarchy is meant to reflect the output of [[com.rabbitmq.client.impl.ValueReader.readFieldValue]]
    final case class BigDecimalVal(value: BigDecimal)            extends AmqpHeaderVal
    final case class DateVal(value: Date)                        extends AmqpHeaderVal
    final case class TableVal(value: Map[String, AmqpHeaderVal]) extends AmqpHeaderVal
    final case class ByteVal(value: Byte)                        extends AmqpHeaderVal
    final case class DoubleVal(value: Double)                    extends AmqpHeaderVal
    final case class FloatVal(value: Float)                      extends AmqpHeaderVal
    final case class ShortVal(value: Short)                      extends AmqpHeaderVal
    final case class ByteArrayVal(value: Array[Byte])            extends AmqpHeaderVal
    final case class BooleanVal(value: Boolean)                  extends AmqpHeaderVal
    final case class IntVal(value: Int)                          extends AmqpHeaderVal
    final case class LongVal(value: Long)                        extends AmqpHeaderVal
    final case class StringVal(value: String)                    extends AmqpHeaderVal
    final case class LongStringVal(value: LongString)            extends AmqpHeaderVal
    final case class ArrayVal(v: Vector[AmqpHeaderVal])          extends AmqpHeaderVal
    case object NullVal                                          extends AmqpHeaderVal

    /**
      * This method is meant purely to translate the output of [[com.rabbitmq.client.impl.ValueReader.readFieldValue]]. As such
      */
    def from(value: AnyRef): AmqpHeaderVal = value match {
      case bd: java.math.BigDecimal => BigDecimalVal(bd)
      case d: java.util.Date        => DateVal(d)
      // Looking at com.rabbitmq.client.impl.ValueReader.readFieldValue reveals that java.util.Maps must always be created by com.rabbitmq.client.impl.ValueReader.readTable, whose Maps must always be of this type, even if at runtime type erasure removes the inner types.
      // This makes us safe from ClassCastExceptions down the road.
      case t: java.util.Map[String @unchecked, AmqpHeaderVal @unchecked] => TableVal(t.asScala.toMap)
      case byte: java.lang.Byte                                          => ByteVal(byte)
      case double: java.lang.Double                                      => DoubleVal(double)
      case float: java.lang.Float                                        => FloatVal(float)
      case short: java.lang.Short                                        => ShortVal(short)
      case byteArray: Array[Byte]                                        => ByteArrayVal(byteArray)
      case b: java.lang.Boolean                                          => BooleanVal(b)
      case i: java.lang.Integer                                          => IntVal(i)
      case l: java.lang.Long                                             => LongVal(l)
      case s: java.lang.String                                           => StringVal(s)
      case ls: LongString                                                => LongStringVal(ls)
      // Looking at com.rabbitmq.client.impl.ValueReader.readFieldValue reveals that java.util.Lists must always be created by com.rabbitmq.client.impl.ValueReader.readArray, whose values must are then recursively created by com.rabbitmq.client.impl.ValueReader.readFieldValue, which indicates that the inner type can never be anything other than the types represented by AmqpHeaderVal
      // This makes us safe from ClassCastExceptions down the road.
      case a: java.util.List[AmqpHeaderVal @unchecked] => ArrayVal(a.asScala.toVector)
      case null                                        => NullVal
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
      replyTo: Option[String] = None,
      clusterId: Option[String] = None,
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
        replyTo = Option(basicProps.getReplyTo),
        clusterId = Option(basicProps.getClusterId),
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
          .replyTo(props.replyTo.orNull)
          .clusterId(props.clusterId.orNull)
          // Note we don't use mapValues here to maintain compatibility between
          // Scala 2.12 and 2.13
          .headers(props.headers.map { case (key, value) => (key, value.impure) }.asJava)
          .build()
    }
  }

  case class AmqpEnvelope[A](deliveryTag: DeliveryTag,
                             payload: A,
                             properties: AmqpProperties,
                             exchangeName: ExchangeName,
                             routingKey: RoutingKey,
                             redelivered: Boolean)
  case class AmqpMessage[A](payload: A, properties: AmqpProperties)

  object AmqpEnvelope {
    private def encoding[F[_]](implicit F: ApplicativeError[F, Throwable]): EnvelopeDecoder[F, Option[Charset]] =
      Kleisli(_.properties.contentEncoding.traverse(n => F.catchNonFatal(Charset.forName(n))))

    // usually this would go in the EnvelopeDecoder companion object, but since that's only a type alias,
    // we need to put it here for the compiler to find it during implicit search
    implicit def stringDecoder[F[_]: ApplicativeError[?[_], Throwable]]: EnvelopeDecoder[F, String] =
      (EnvelopeDecoder.payload[F], encoding[F]).mapN((p, e) => new String(p, e.getOrElse(UTF_8)))

  }

  object AmqpMessage {
    implicit def stringEncoder[F[_]: Applicative]: MessageEncoder[F, String] =
      Kleisli { str =>
        AmqpMessage(str.getBytes(UTF_8), AmqpProperties.empty.copy(contentEncoding = Some(UTF_8.name()))).pure[F]
      }
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
  case class ReplyCode(value: Int)        extends AnyVal
  case class ReplyText(value: String)     extends AnyVal
  case class AmqpBody(value: Array[Byte]) extends AnyVal

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
