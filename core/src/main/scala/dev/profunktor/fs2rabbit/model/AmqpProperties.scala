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

package dev.profunktor.fs2rabbit.model

import cats.*
import cats.implicits.*
import com.rabbitmq.client.AMQP

import java.time.Instant
import java.util.Date
import scala.jdk.CollectionConverters.*

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
    timestamp: Option[Instant] = None,
    headers: Headers = Headers.empty
)
object AmqpProperties {

  def empty: AmqpProperties = AmqpProperties()

  private def tupled(p: AmqpProperties): (
      Option[String],
      Option[String],
      Option[Int],
      Option[DeliveryMode],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[Instant],
      Headers
  ) =
    p match {
      case AmqpProperties(a, b, c, d, e, f, g, h, i, j, k, l, m, n) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n)
    }

  implicit val amqpPropertiesEq: Eq[AmqpProperties] = {
    implicit val instantOrder: Order[Instant] = instantOrderWithSecondPrecision
    Eq.by(ap => tupled(ap))
  }

  /** It is possible to construct an [[AMQP.BasicProperties]] that will cause this method to crash, hence it is unsafe.
    * It is meant to be passed values that are created by the underlying RabbitMQ Java client library or other values
    * that you are certain are well-formed (that is they conform to the AMQP spec).
    *
    * A user of the library should most likely not be calling this directly, and instead should be constructing an
    * [[AmqpProperties]] directly.
    */
  private[fs2rabbit] def unsafeFrom(basicProps: AMQP.BasicProperties): AmqpProperties =
    AmqpProperties(
      contentType = Option(basicProps.getContentType),
      contentEncoding = Option(basicProps.getContentEncoding),
      priority = Option[Integer](basicProps.getPriority).map(Int.unbox),
      deliveryMode = Option(basicProps.getDeliveryMode).map(DeliveryMode.unsafeFromInt(_)),
      correlationId = Option(basicProps.getCorrelationId),
      messageId = Option(basicProps.getMessageId),
      `type` = Option(basicProps.getType),
      userId = Option(basicProps.getUserId),
      appId = Option(basicProps.getAppId),
      expiration = Option(basicProps.getExpiration),
      replyTo = Option(basicProps.getReplyTo),
      clusterId = Option(basicProps.getClusterId),
      timestamp = Option(basicProps.getTimestamp).map(_.toInstant),
      headers = Headers.unsafeFromMap(
        Option(basicProps.getHeaders)
          .fold(Map.empty[String, Object])(_.asScala.toMap)
      )
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
        .timestamp(props.timestamp.map(Date.from).orNull)
        // Note we don't use mapValues here to maintain compatibility between
        // Scala 2.12 and 2.13
        .headers(props.headers.toMap.map { case (key, value) => (key, value.toValueWriterCompatibleJava) }.asJava)
        .build()
  }
}
