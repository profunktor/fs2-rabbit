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

package com.itv.fs2rabbit

import arguments.Arguments
import model.AmqpHeaderVal.{IntVal, LongVal, StringVal}
import com.rabbitmq.client.impl.LongStringHelper
import com.rabbitmq.client.{AMQP, Channel, LongString}
import fs2.{Sink, Stream}

object model {

  type StreamAcker[F[_]]         = Sink[F, AckResult]
  type StreamConsumer[F[_]]      = Stream[F, AmqpEnvelope]
  type StreamAckerConsumer[F[_]] = (StreamAcker[F], StreamConsumer[F])
  type StreamPublisher[F[_]]     = Sink[F, AmqpMessage[String]]

  trait AMQPChannel {
    def value: Channel
  }
  case class RabbitChannel(value: Channel) extends AMQPChannel

  case class ExchangeName(value: String) extends AnyVal
  case class QueueName(value: String)    extends AnyVal
  case class RoutingKey(value: String)   extends AnyVal
  case class DeliveryTag(value: Long)    extends AnyVal

  case class ConsumerArgs(consumerTag: String, noLocal: Boolean, exclusive: Boolean, args: Arguments)
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
    }
  }

  object AmqpHeaderVal {
    final case class IntVal(value: Int)       extends AmqpHeaderVal
    final case class LongVal(value: Long)     extends AmqpHeaderVal
    final case class StringVal(value: String) extends AmqpHeaderVal

    def from(value: AnyRef): AmqpHeaderVal = value match {
      case ls: LongString       => StringVal(new String(ls.getBytes, "UTF-8"))
      case s: String            => StringVal(s)
      case l: java.lang.Long    => LongVal(l)
      case i: java.lang.Integer => IntVal(i)
    }
  }

  case class AmqpProperties(contentType: Option[String],
                            contentEncoding: Option[String],
                            priority: Option[Int],
                            deliveryMode: Option[DeliveryMode],
                            headers: Map[String, AmqpHeaderVal])

  object AmqpProperties {
    import scala.collection.JavaConverters._

    def empty = AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])

    def from(basicProps: AMQP.BasicProperties): AmqpProperties =
      AmqpProperties(
        Option(basicProps.getContentType),
        Option(basicProps.getContentEncoding),
        Option[Integer](basicProps.getPriority).map(Int.unbox),
        Option(basicProps.getDeliveryMode).map(DeliveryMode.from(_)),
        Option(basicProps.getHeaders)
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
          .headers(props.headers.mapValues[AnyRef](_.impure).asJava)
          .build()
    }
  }

  case class AmqpEnvelope(deliveryTag: DeliveryTag, payload: String, properties: AmqpProperties)
  case class AmqpMessage[A](payload: A, properties: AmqpProperties)

  // Binding
  case class QueueBindingArgs(value: Arguments)    extends AnyVal
  case class ExchangeBindingArgs(value: Arguments) extends AnyVal

  // Declaration
  case class QueueDeclarationArgs(value: Arguments)    extends AnyVal
  case class ExchangeDeclarationArgs(value: Arguments) extends AnyVal

}
