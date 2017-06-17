package com.github.gvolpe.fs2rabbit

import com.rabbitmq.client.impl.LongStringHelper
import com.rabbitmq.client.{AMQP, LongString}
import fs2.{Sink, Stream}

import scala.language.higherKinds

object model {

  case class ExchangeName(name: String)
  case class QueueName(name: String)
  case class RoutingKey(name: String)

  type DeliveryTag    = Long

  object ExchangeType extends Enumeration {
    type ExchangeType = Value
    val Direct, FanOut, Headers, Topic = Value
  }

  sealed trait AckResult extends Product with Serializable
  final case class Ack(deliveryTag: DeliveryTag) extends AckResult
  final case class NAck(deliveryTag: DeliveryTag) extends AckResult

  type StreamAcker[F[_]]          = Sink[F, AckResult]
  type StreamConsumer[F[_]]       = Stream[F, AmqpEnvelope]
  type StreamAckerConsumer[F[_]]  = (StreamAcker[F], StreamConsumer[F])
  type StreamPublisher[F[_]]      = Sink[F, AmqpMessage[String]]

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
        basicProps.getHeaders.asScala.toMap.map {
          case (k,v) => k -> AmqpHeaderVal.from(v)
        }
      )

    implicit class AmqpPropertiesOps(props: AmqpProperties) {
      def asBasicProps: AMQP.BasicProperties = new AMQP.BasicProperties.Builder()
        .contentType(props.contentType.orNull)
        .contentEncoding(props.contentEncoding.orNull)
        .headers(props.headers.mapValues[AnyRef](_.impure).asJava)
        .build()
    }
  }

  case class AmqpEnvelope(deliveryTag: DeliveryTag, payload: String, properties: AmqpProperties)

  case class AmqpMessage[A](payload: A, properties: AmqpProperties)

  // Generic type arguments for methods like queueBind, queueDeclare, etc
  case class QueueDeclarationArgs(value: Map[String, AnyRef])
  case class QueueBindingArgs(value: Map[String, AnyRef])

  case class ExchangeDeclarationArgs(value: Map[String, AnyRef])
  case class ExchangeBindingArgs(value: Map[String, AnyRef])


}
