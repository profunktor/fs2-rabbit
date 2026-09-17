/*
 * Copyright 2017-2026 ProfunKtor
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

package dev.profunktor.fs2rabbit.otel4s
package internal

import dev.profunktor.fs2rabbit.model.{AmqpEnvelope, AmqpMessage, ExchangeName, QueueName, RoutingKey}
import org.typelevel.otel4s.{Attribute, AttributeKey, Attributes}

private[otel4s] object Semconv {

  object Const {
    val MessagingSystem: Attribute[String] = Attribute("messaging.system", "rabbitmq")
  }

  object Keys {
    val DestinationAnonymous: AttributeKey[Boolean]       = AttributeKey[Boolean]("messaging.destination.anonymous")
    val DestinationName: AttributeKey[String]             = AttributeKey[String]("messaging.destination.name")
    val OperationName: AttributeKey[String]               = AttributeKey[String]("messaging.operation.name")
    val OperationType: AttributeKey[String]               = AttributeKey[String]("messaging.operation.type")
    val ClientId: AttributeKey[String]                    = AttributeKey[String]("messaging.client.id")
    val MessageConversationId: AttributeKey[String]       = AttributeKey[String]("messaging.message.conversation_id")
    val MessageId: AttributeKey[String]                   = AttributeKey[String]("messaging.message.id")
    val RabbitDestinationRoutingKey: AttributeKey[String] =
      AttributeKey[String]("messaging.rabbitmq.destination.routing_key")
    val RabbitMessageDeliveryTag: AttributeKey[Long]      =
      AttributeKey[Long]("messaging.rabbitmq.message.delivery_tag")
  }

  def publishSpanContext(
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      message: AmqpMessage[Array[Byte]]
  ): PublishSpanContext =
    PublishSpanContext(
      exchangeName,
      routingKey,
      producerDestinationName(exchangeName, routingKey),
      message.properties.messageId,
      message.properties.correlationId
    )

  def processSpanContext[A](queueName: QueueName, envelope: AmqpEnvelope[A]): ProcessSpanContext =
    ProcessSpanContext(
      queueName,
      envelope.exchangeName,
      envelope.routingKey,
      consumerDestinationName(envelope.exchangeName, envelope.routingKey, queueName),
      envelope.deliveryTag,
      envelope.properties.messageId,
      envelope.properties.correlationId,
      envelope.redelivered
    )

  def publishAttributes(context: PublishSpanContext, clientId: Option[String]): Attributes = {
    val builder = baseBuilder("publish", "send", context.destinationName, clientId)
    builder.addAll(Keys.RabbitDestinationRoutingKey.maybe(nonEmpty(context.routingKey.value)))
    builder.addAll(Keys.MessageId.maybe(context.messageId))
    builder.addAll(Keys.MessageConversationId.maybe(context.conversationId))
    builder.result()
  }

  def processAttributes(context: ProcessSpanContext, clientId: Option[String]): Attributes = {
    val builder = baseBuilder("process", "process", context.destinationName, clientId)
    builder.addAll(Keys.RabbitDestinationRoutingKey.maybe(nonEmpty(context.routingKey.value)))
    builder.addOne(Keys.RabbitMessageDeliveryTag(context.deliveryTag.value))
    builder.addAll(Keys.MessageId.maybe(context.messageId))
    builder.addAll(Keys.MessageConversationId.maybe(context.conversationId))
    builder.addAll(Keys.DestinationAnonymous.maybe(Option.when(isGeneratedQueueName(context.queueName.value))(true)))
    builder.result()
  }

  def publishLinkAttributes(context: PublishSpanContext): Attributes = {
    val builder = Attributes.newBuilder
    builder.addOne(Keys.DestinationName(context.destinationName))
    builder.addAll(Keys.RabbitDestinationRoutingKey.maybe(nonEmpty(context.routingKey.value)))
    builder.addAll(Keys.MessageId.maybe(context.messageId))
    builder.result()
  }

  def processLinkAttributes(context: ProcessSpanContext): Attributes = {
    val builder = Attributes.newBuilder
    builder.addOne(Keys.DestinationName(context.destinationName))
    builder.addAll(Keys.RabbitDestinationRoutingKey.maybe(nonEmpty(context.routingKey.value)))
    builder.addOne(Keys.RabbitMessageDeliveryTag(context.deliveryTag.value))
    builder.addAll(Keys.MessageId.maybe(context.messageId))
    builder.result()
  }

  def producerDestinationName(exchangeName: ExchangeName, routingKey: RoutingKey): String =
    joinDestination(exchangeName.value, routingKey.value).getOrElse("amq.default")

  def consumerDestinationName(exchangeName: ExchangeName, routingKey: RoutingKey, queueName: QueueName): String = {
    val queue = Option.when(queueName.value != routingKey.value)(queueName.value).getOrElse("")
    joinDestination(exchangeName.value, routingKey.value, queue).getOrElse("amq.default")
  }

  private def baseBuilder(
      operationName: String,
      operationType: String,
      destinationName: String,
      clientId: Option[String]
  ): Attributes.Builder = {
    val builder = Attributes.newBuilder
    builder.addOne(Const.MessagingSystem)
    builder.addOne(Keys.OperationName(operationName))
    builder.addOne(Keys.OperationType(operationType))
    builder.addOne(Keys.DestinationName(destinationName))
    builder.addAll(Keys.ClientId.maybe(clientId))
    builder
  }

  private def joinDestination(parts: String*): Option[String] = {
    val nonEmptyParts = parts.filter(_.nonEmpty)
    Option.when(nonEmptyParts.nonEmpty)(nonEmptyParts.mkString(":"))
  }

  private def nonEmpty(value: String): Option[String] =
    Option.when(value.nonEmpty)(value)

  private def isGeneratedQueueName(queue: String): Boolean =
    queue.startsWith("amq.gen-") || queue.startsWith("spring.gen-") || isCanonicalUuid(queue)

  private def isCanonicalUuid(value: String): Boolean =
    value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}
