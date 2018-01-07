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

package com.github.gvolpe.fs2rabbit

import com.rabbitmq.client.impl.LongStringHelper
import com.rabbitmq.client.{AMQP, LongString}
import fs2.{Sink, Stream}

import scala.reflect.ClassTag

object model {

  class ExchangeName(val value: String) extends AnyVal {
    override def toString: String = value
  }
  class QueueName(val value: String) extends AnyVal {
    override def toString: String = value
  }
  class RoutingKey(val value: String) extends AnyVal {
    override def toString: String = value
  }
  class DeliveryTag(val value: Long) extends AnyVal {
    override def toString: String = value.toString
  }

  case class ConsumerArgs(consumerTag: String, noLocal: Boolean, exclusive: Boolean, args: Map[String, AnyRef])
  case class BasicQos(prefetchSize: Int, prefetchCount: Int, global: Boolean = false)

  object ExchangeType extends Enumeration {
    type ExchangeType = Value
    val Direct, FanOut, Headers, Topic = Value
  }

  sealed trait AckResult                          extends Product with Serializable
  final case class Ack(deliveryTag: DeliveryTag)  extends AckResult
  final case class NAck(deliveryTag: DeliveryTag) extends AckResult

  type StreamAcker[F[_]]         = Sink[F, AckResult]
  type StreamConsumer[F[_]]      = Stream[F, AmqpEnvelope]
  type StreamAckerConsumer[F[_]] = (StreamAcker[F], StreamConsumer[F])
  type StreamPublisher[F[_]]     = Sink[F, AmqpMessage[String]]

  sealed trait AmqpHeaderVal extends Product with Serializable {
    def impure: AnyRef = this match {
      case StringVal(v) => LongStringHelper.asLongString(v)
      case IntVal(v)    => Int.box(v)
      case LongVal(v)   => Long.box(v)
    }
  }
  final case class IntVal(v: Int)       extends AmqpHeaderVal
  final case class LongVal(v: Long)     extends AmqpHeaderVal
  final case class StringVal(v: String) extends AmqpHeaderVal

  object AmqpHeaderVal {
    def from(value: AnyRef): AmqpHeaderVal = value match {
      case ls: LongString       => StringVal(new String(ls.getBytes, "UTF-8"))
      case s: String            => StringVal(s)
      case l: java.lang.Long    => LongVal(l)
      case i: java.lang.Integer => IntVal(i)
    }
  }

  case class AmqpProperties(contentType: Option[String],
                            contentEncoding: Option[String],
                            headers: Map[String, AmqpHeaderVal])

  object AmqpProperties {
    import scala.collection.JavaConverters._

    def empty = AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])

    def from(basicProps: AMQP.BasicProperties): AmqpProperties =
      AmqpProperties(
        Option(basicProps.getContentType),
        Option(basicProps.getContentEncoding),
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
          .headers(props.headers.mapValues[AnyRef](_.impure).asJava)
          .build()
    }
  }

  case class AmqpEnvelope(deliveryTag: DeliveryTag, payload: String, properties: AmqpProperties)

  case class AmqpMessage[A](payload: A, properties: AmqpProperties)

  implicit class StringValueClasses(value: String) {
    def as[A](implicit ct: ClassTag[A]): A =
      ct.runtimeClass.getConstructors.head.newInstance(value).asInstanceOf[A]
  }

  // Binding
  case class QueueBindingArgs(value: Map[String, AnyRef])
  case class ExchangeBindingArgs(value: Map[String, AnyRef])

  // Declaration
  case class QueueDeclarationArgs(value: Map[String, AnyRef])
  case class ExchangeDeclarationArgs(value: Map[String, AnyRef])

}
