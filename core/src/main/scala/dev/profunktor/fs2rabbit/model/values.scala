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

package dev.profunktor.fs2rabbit.model

import cats._
import cats.implicits._
import cats.kernel.CommutativeSemigroup
import com.rabbitmq.client.{Channel, Connection}
import dev.profunktor.fs2rabbit.arguments.Arguments

trait AMQPChannel {
  def value: Channel
}
case class RabbitChannel(value: Channel) extends AMQPChannel

trait AMQPConnection {
  def value: Connection
}
case class RabbitConnection(value: Connection) extends AMQPConnection

case class ExchangeName(value: String) extends AnyVal
object ExchangeName                    extends (String => ExchangeName) {
  implicit val exchangeNameOrder: Order[ExchangeName] = Order.by(_.value)
}

case class QueueName(value: String) extends AnyVal
object QueueName                    extends (String => QueueName) {
  implicit val queueNameOrder: Order[QueueName] = Order.by(_.value)
}

case class RoutingKey(value: String) extends AnyVal
object RoutingKey                    extends (String => RoutingKey) {
  implicit val routingKeyOrder: Order[RoutingKey] = Order.by(_.value)
}

case class DeliveryTag(value: Long) extends AnyVal
object DeliveryTag                  extends (Long => DeliveryTag) {
  implicit val deliveryTagOrder: Order[DeliveryTag]                               = Order.by(_.value)
  implicit val deliveryTagCommutativeSemigroup: CommutativeSemigroup[DeliveryTag] =
    CommutativeSemigroup.instance(deliveryTagOrder.max)
}

case class ConsumerTag(value: String) extends AnyVal
object ConsumerTag                    extends (String => ConsumerTag) {
  implicit val consumerTagOrder: Order[ConsumerTag] = Order.by(_.value)
}

sealed trait QueueType extends Product with Serializable {
  def asString: String = this match {
    case QueueType.Classic => "classic"
    case QueueType.Quorum  => "quorum"
    case QueueType.Stream  => "stream"
  }
}
object QueueType {
  case object Classic extends QueueType
  case object Quorum  extends QueueType
  case object Stream  extends QueueType
}

sealed trait ExchangeType extends Product with Serializable {
  def asString: String = this match {
    case ExchangeType.Direct              => "direct"
    case ExchangeType.FanOut              => "fanout"
    case ExchangeType.Headers             => "headers"
    case ExchangeType.Topic               => "topic"
    case ExchangeType.`X-Delayed-Message` => "x-delayed-message"
  }
}
object ExchangeType {
  case object Direct  extends ExchangeType
  case object FanOut  extends ExchangeType
  case object Headers extends ExchangeType
  case object Topic   extends ExchangeType
  case object `X-Delayed-Message`
      extends ExchangeType // for use with the plugin https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/
}

sealed abstract class DeliveryMode(val value: Int) extends Product with Serializable
object DeliveryMode {
  case object NonPersistent extends DeliveryMode(1)
  case object Persistent    extends DeliveryMode(2)

  @deprecated("Use fromInt or unsafeFromInt", "5.3.0")
  def from(value: Int): DeliveryMode =
    unsafeFromInt(value)

  def unsafeFromInt(value: Int): DeliveryMode =
    fromInt(value)
      .getOrElse(throw new IllegalArgumentException(s"Invalid delivery mode from Int: $value"))

  def fromInt(value: Int): Option[DeliveryMode] = value match {
    case 1 => Some(NonPersistent)
    case 2 => Some(Persistent)
    case _ => None
  }

  implicit val deliveryModeOrder: Order[DeliveryMode] = Order.by(_.value)
}

sealed trait AckResult extends Product with Serializable
object AckResult {
  final case class Ack(deliveryTag: DeliveryTag)    extends AckResult
  final case class NAck(deliveryTag: DeliveryTag)   extends AckResult
  final case class Reject(deliveryTag: DeliveryTag) extends AckResult
}

final case class AckMultiple(multiple: Boolean) extends AnyVal

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
case class ReplyCode(value: Int)              extends AnyVal
case class ReplyText(value: String)           extends AnyVal
case class AmqpBody(value: Array[Byte])       extends AnyVal
case class PublishingFlag(mandatory: Boolean) extends AnyVal
