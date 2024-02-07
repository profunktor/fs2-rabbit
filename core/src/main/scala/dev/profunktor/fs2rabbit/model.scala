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

package dev.profunktor.fs2rabbit

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import cats.data._
import cats.implicits._
import cats.kernel.CommutativeSemigroup
import cats._
import com.rabbitmq.client.{AMQP, Channel, Connection, LongString}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import scala.jdk.CollectionConverters._
import fs2.Stream
import scodec.bits.ByteVector
import scodec.interop.cats._

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
  object ExchangeName                    extends (String => ExchangeName) {
    implicit val exchangeNameOrder: Order[ExchangeName] = Order.by(_.value)
  }
  case class QueueName(value: String)    extends AnyVal
  object QueueName                       extends (String => QueueName)    {
    implicit val queueNameOrder: Order[QueueName] = Order.by(_.value)
  }
  case class RoutingKey(value: String)   extends AnyVal
  object RoutingKey                      extends (String => RoutingKey)   {
    implicit val routingKeyOrder: Order[RoutingKey] = Order.by(_.value)
  }
  case class DeliveryTag(value: Long)    extends AnyVal
  object DeliveryTag                     extends (Long => DeliveryTag)    {
    implicit val deliveryTagOrder: Order[DeliveryTag]                               = Order.by(_.value)
    implicit val deliveryTagCommutativeSemigroup: CommutativeSemigroup[DeliveryTag] =
      CommutativeSemigroup.instance(deliveryTagOrder.max)
  }
  case class ConsumerTag(value: String)  extends AnyVal
  object ConsumerTag                     extends (String => ConsumerTag)  {
    implicit val consumerTagOrder: Order[ConsumerTag] = Order.by(_.value)
  }

  case class ConsumerArgs(consumerTag: ConsumerTag, noLocal: Boolean, exclusive: Boolean, args: Arguments)
  case class BasicQos(prefetchSize: Int, prefetchCount: Int, global: Boolean = false)

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

    def from(value: Int): DeliveryMode = value match {
      case 1 => NonPersistent
      case 2 => Persistent
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

  /** A string whose UTF-8 encoded representation is 255 bytes or less.
    *
    * Parts of the AMQP spec call for the use of such strings.
    */
  sealed abstract case class ShortString private (str: String)
  object ShortString {
    val MaxByteLength = 255

    def from(str: String): Option[ShortString] =
      if (str.getBytes("utf-8").length <= MaxByteLength) {
        Some(new ShortString(str) {})
      } else {
        None
      }

    /** This bypasses the safety check that [[from]] has. This is meant only for situations where you are certain the
      * string cannot be larger than [[MaxByteLength]] (e.g. string literals).
      */
    def unsafeFrom(str: String): ShortString = new ShortString(str) {}

    implicit val shortStringOrder: Order[ShortString]       = Order.by(_.str)
    implicit val shortStringOrdering: Ordering[ShortString] = Order[ShortString].toOrdering
  }

  /** This hierarchy is meant to reflect the output of [[com.rabbitmq.client.impl.ValueReader.readFieldValue]] in a
    * type-safe way.
    *
    * Note that we don't include LongString here because of some ambiguity in how RabbitMQ's Java client deals with it.
    * While it will happily write out LongStrings and Strings separately, when reading it will always interpret a String
    * as a LongString and so will never return a normal String. This means that if we included separate LongStringVal
    * and StringVals we could have encode-decode round-trip differences (taking a String sending it off to RabbitMQ and
    * reading it back will result in a LongString). We therefore collapse both LongStrings and Strings into a single
    * StringVal backed by an ordinary String.
    *
    * Note that this type hierarchy is NOT exactly identical to the AMQP 0-9-1 spec. This is partially because RabbitMQ
    * does not exactly follow the spec itself (see https://www.rabbitmq.com/amqp-0-9-1-errata.html#section_3) and also
    * because the underlying Java client chooses to try to map the RabbitMQ types into Java primitive types when
    * possible, collapsing a lot of the signed and unsigned types because Java does not have the signed and unsigned
    * equivalents.
    */
  sealed trait AmqpFieldValue extends Product with Serializable {

    /** The opposite of [[AmqpFieldValue.unsafeFrom]]. Turns an [[AmqpFieldValue]] into something that can be processed
      * by [[com.rabbitmq.client.impl.ValueWriter]].
      */
    def toValueWriterCompatibleJava: AnyRef
  }

  object AmqpFieldValue {

    /** A type for AMQP timestamps.
      *
      * Note that this is only accurate to the second (as supported by the AMQP spec and the underlying RabbitMQ
      * implementation).
      */
    sealed abstract case class TimestampVal private (instantWithOneSecondAccuracy: Instant) extends AmqpFieldValue {
      override def toValueWriterCompatibleJava: Date = Date.from(instantWithOneSecondAccuracy)
    }
    object TimestampVal {
      def from(instant: Instant): TimestampVal =
        new TimestampVal(instant.truncatedTo(ChronoUnit.SECONDS)) {}

      def from(date: Date): TimestampVal = from(date.toInstant)

      implicit val timestampOrder: Order[TimestampVal] =
        Order.by[TimestampVal, Instant](_.instantWithOneSecondAccuracy)(instantOrderWithSecondPrecision)
    }

    /** A type for precise decimal values. Note that while it is backed by a [[BigDecimal]] (just like the underlying
      * Java library), there is a limit on the size and precision of the decimal: its unscaled representation cannot
      * exceed 4 bytes due to the AMQP spec and its scale component must be an octet.
      */
    sealed abstract case class DecimalVal private (sizeLimitedBigDecimal: BigDecimal) extends AmqpFieldValue {
      override def toValueWriterCompatibleJava: java.math.BigDecimal = sizeLimitedBigDecimal.bigDecimal
    }
    object DecimalVal {
      val MaxUnscaledBits: Int = 32

      val MaxScaleValue: Int = 255

      /** The AMQP 0.9.1 standard specifies that the scale component of a decimal must be an octet (i.e. between 0 and
        * 255) and that its unscaled component must be a 32-bit integer. If those criteria are not met, then we get back
        * None.
        */
      def from(bigDecimal: BigDecimal): Option[DecimalVal] =
        if (
          getFullBitLengthOfUnscaled(bigDecimal) > MaxUnscaledBits ||
          bigDecimal.scale > MaxScaleValue ||
          bigDecimal.scale < 0
        ) {
          None
        } else {
          Some(new DecimalVal(bigDecimal) {})
        }

      /** Only use if you're certain that the [[BigDecimal]]'s representation meets the requirements of a [[DecimalVal]]
        * (e.g. you are constructing one using literals).
        *
        * Almost always you should be using [[from]].
        */
      def unsafeFrom(bigDecimal: BigDecimal): DecimalVal =
        new DecimalVal(bigDecimal) {}

      private def getFullBitLengthOfUnscaled(bigDecimal: BigDecimal): Int =
        // Note that we add back 1 here because bitLength ignores the sign bit,
        // reporting back an answer that's one bit too small.
        bigDecimal.bigDecimal.unscaledValue.bitLength + 1

      implicit val decimalValOrder: Order[DecimalVal] = Order.by(_.sizeLimitedBigDecimal)
    }

    final case class TableVal(value: Map[ShortString, AmqpFieldValue]) extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.util.Map[String, AnyRef] =
        value.map { case (key, v) => key.str -> v.toValueWriterCompatibleJava }.asJava
    }
    object TableVal                                                    extends (Map[ShortString, AmqpFieldValue] => TableVal) {
      implicit val tableValEq: Eq[TableVal] = Eq.by(_.value)
    }
    final case class ByteVal(value: Byte)                              extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.lang.Byte = Byte.box(value)
    }
    object ByteVal                                                     extends (Byte => ByteVal)                              {
      implicit val byteValOrder: Order[ByteVal] = Order.by(_.value)
    }
    final case class DoubleVal(value: Double)                          extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.lang.Double = Double.box(value)
    }
    object DoubleVal                                                   extends (Double => DoubleVal)                          {
      implicit val doubleValOrder: Order[DoubleVal] = Order.by(_.value)
    }
    final case class FloatVal(value: Float)                            extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.lang.Float = Float.box(value)
    }
    object FloatVal                                                    extends (Float => FloatVal)                            {
      implicit val floatValOrder: Order[FloatVal] = Order.by(_.value)
    }
    final case class ShortVal(value: Short)                            extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.lang.Short = Short.box(value)
    }
    object ShortVal                                                    extends (Short => ShortVal)                            {
      implicit val shortValOrder: Order[ShortVal] = Order.by(_.value)
    }
    final case class ByteArrayVal(value: ByteVector)                   extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: Array[Byte] = value.toArray
    }
    object ByteArrayVal                                                extends (ByteVector => ByteArrayVal)                   {
      implicit val byteArrayValEq: Eq[ByteArrayVal] = Eq.by(_.value)
    }
    final case class BooleanVal(value: Boolean)                        extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.lang.Boolean = Boolean.box(value)
    }
    object BooleanVal                                                  extends (Boolean => BooleanVal)                        {
      implicit val booleanValOrder: Order[BooleanVal] = Order.by(_.value)
    }
    final case class IntVal(value: Int)                                extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.lang.Integer = Int.box(value)
    }
    object IntVal                                                      extends (Int => IntVal)                                {
      implicit val intValOrder: Order[IntVal] = Order.by(_.value)
    }
    final case class LongVal(value: Long)                              extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.lang.Long = Long.box(value)
    }
    object LongVal                                                     extends (Long => LongVal)                              {
      implicit val longValOrder: Order[LongVal] = Order.by(_.value)
    }
    final case class StringVal(value: String)                          extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: String = value
    }
    object StringVal                                                   extends (String => StringVal)                          {
      implicit val stringValOrder: Order[StringVal] = Order.by(_.value)
    }
    final case class ArrayVal(value: Vector[AmqpFieldValue])           extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: java.util.List[AnyRef] = value.map(_.toValueWriterCompatibleJava).asJava
    }
    object ArrayVal                                                    extends (Vector[AmqpFieldValue] => ArrayVal)           {
      implicit val arrayValEq: Eq[ArrayVal] = Eq.by(_.value)
    }
    case object NullVal                                                extends AmqpFieldValue                                 {
      override def toValueWriterCompatibleJava: Null = null

      implicit val nullValOrder: Order[NullVal.type] = Order.allEqual
    }

    /** This method is meant purely to translate the output of [[com.rabbitmq.client.impl.ValueReader.readFieldValue]].
      * As such it is NOT total and will blow up if you pass it a class which
      * [[com.rabbitmq.client.impl.ValueReader.readFieldValue]] does not output.
      *
      * As a user of this library, you almost certainly be constructing [[AmqpFieldValue]]s directly instead of using
      * this method.
      */
    private[fs2rabbit] def unsafeFrom(value: AnyRef): AmqpFieldValue = value match {
      // It's safe to call unsafeFromBigDecimal here because if the value came
      // from readFieldValue, we're assured that the check on BigDecimal
      // representation size must have already occurred because ValueReader will
      // only read a maximum of 4 bytes before bailing out (similarly it will
      // read no more than the first 8 bits to determine scale).
      case bd: java.math.BigDecimal                               => DecimalVal.unsafeFrom(bd)
      case ts: java.time.Instant                                  => TimestampVal.from(ts)
      case d: java.util.Date                                      => TimestampVal.from(d)
      // Looking at com.rabbitmq.client.impl.ValueReader.readFieldValue reveals
      // that java.util.Maps must always be created by
      // com.rabbitmq.client.impl.ValueReader.readTable, whose Maps must always
      // be of this type, even if at runtime type erasure removes the inner types.
      // This makes us safe from ClassCastExceptions down the road.
      case t: java.util.Map[String @unchecked, AnyRef @unchecked] =>
        // ShortString.unsafeOf is safe to use here for a rather subtle reason.
        // Even though ValueReader.readShortstr doesn't perform any explicit
        // validation that a short string is 255 chars or less, it only reads
        // one byte to determine how large of a byte array to allocate for the
        // string which means the length cannot possibly exceed 255.
        TableVal(t.asScala.toMap.map { case (key, v) => ShortString.unsafeFrom(key) -> unsafeFrom(v) })
      case byte: java.lang.Byte                                   => ByteVal(byte)
      case double: java.lang.Double                               => DoubleVal(double)
      case float: java.lang.Float                                 => FloatVal(float)
      case short: java.lang.Short                                 => ShortVal(short)
      case byteArray: Array[Byte]                                 => ByteArrayVal(ByteVector(byteArray))
      case b: java.lang.Boolean                                   => BooleanVal(b)
      case i: java.lang.Integer                                   => IntVal(i)
      case l: java.lang.Long                                      => LongVal(l)
      case s: java.lang.String                                    => StringVal(s)
      case ls: LongString                                         => StringVal(ls.toString)
      // Looking at com.rabbitmq.client.impl.ValueReader.readFieldValue reveals
      // that java.util.Lists must always be created by
      // com.rabbitmq.client.impl.ValueReader.readArray, whose values must are
      // then recursively created by
      // com.rabbitmq.client.impl.ValueReader.readFieldValue, which indicates
      // that the inner type can never be anything other than the types
      // represented by AmqpHeaderVal
      // This makes us safe from ClassCastExceptions down the road.
      case a: java.util.List[AnyRef @unchecked]                   => ArrayVal(a.asScala.toVector.map(unsafeFrom))
      case null                                                   => NullVal
      case _                                                      => throw new IllegalArgumentException()
    }

    implicit val amqpFieldValueEq: Eq[AmqpFieldValue] = new Eq[AmqpFieldValue] {
      override def eqv(x: AmqpFieldValue, y: AmqpFieldValue): Boolean = (x, y) match {
        case (a: ArrayVal, b: ArrayVal)         => Eq[ArrayVal].eqv(a, b)
        case (a: BooleanVal, b: BooleanVal)     => Eq[BooleanVal].eqv(a, b)
        case (a: ByteArrayVal, b: ByteArrayVal) => Eq[ByteArrayVal].eqv(a, b)
        case (a: ByteVal, b: ByteVal)           => Eq[ByteVal].eqv(a, b)
        case (a: DecimalVal, b: DecimalVal)     => Eq[DecimalVal].eqv(a, b)
        case (a: DoubleVal, b: DoubleVal)       => Eq[DoubleVal].eqv(a, b)
        case (a: FloatVal, b: FloatVal)         => Eq[FloatVal].eqv(a, b)
        case (a: IntVal, b: IntVal)             => Eq[IntVal].eqv(a, b)
        case (a: LongVal, b: LongVal)           => Eq[LongVal].eqv(a, b)
        case (a: NullVal.type, b: NullVal.type) => Eq[NullVal.type].eqv(a, b)
        case (a: ShortVal, b: ShortVal)         => Eq[ShortVal].eqv(a, b)
        case (a: StringVal, b: StringVal)       => Eq[StringVal].eqv(a, b)
        case (a: TableVal, b: TableVal)         => Eq[TableVal].eqv(a, b)
        case (a: TimestampVal, b: TimestampVal) => Eq[TimestampVal].eqv(a, b)
        case _                                  => false
      }
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
      timestamp: Option[Instant] = None,
      headers: Map[String, AmqpFieldValue] = Map.empty
  )

  object AmqpProperties {
    def empty = AmqpProperties()

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
        Map[String, AmqpFieldValue]
    ) =
      p match {
        case AmqpProperties(a, b, c, d, e, f, g, h, i, j, k, l, m, n) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n)
      }

    implicit val amqpPropertiesEq: Eq[AmqpProperties] = {
      implicit val instantOrder: Order[Instant] = instantOrderWithSecondPrecision
      Eq.by(ap => tupled(ap))
    }

    /** It is possible to construct an [[AMQP.BasicProperties]] that will cause this method to crash, hence it is
      * unsafe. It is meant to be passed values that are created by the underlying RabbitMQ Java client library or other
      * values that you are certain are well-formed (that is they conform to the AMQP spec).
      *
      * A user of the library should most likely not be calling this directly, and instead should be constructing an
      * [[AmqpProperties]] directly.
      */
    private[fs2rabbit] def unsafeFrom(basicProps: AMQP.BasicProperties): AmqpProperties =
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
        timestamp = Option(basicProps.getTimestamp).map(_.toInstant),
        headers = Option(basicProps.getHeaders)
          .fold(Map.empty[String, Object])(_.asScala.toMap)
          .map { case (k, v) =>
            k -> AmqpFieldValue.unsafeFrom(v)
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
          .timestamp(props.timestamp.map(Date.from).orNull)
          // Note we don't use mapValues here to maintain compatibility between
          // Scala 2.12 and 2.13
          .headers(props.headers.map { case (key, value) => (key, value.toValueWriterCompatibleJava) }.asJava)
          .build()
    }
  }

  case class AmqpEnvelope[A](
      deliveryTag: DeliveryTag,
      payload: A,
      properties: AmqpProperties,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      redelivered: Boolean
  )
  case class AmqpMessage[A](payload: A, properties: AmqpProperties)

  object AmqpEnvelope {
    private def encoding[F[_]](implicit F: ApplicativeThrow[F]): EnvelopeDecoder[F, Option[Charset]] =
      Kleisli(_.properties.contentEncoding.traverse(n => F.catchNonFatal(Charset.forName(n))))

    // usually this would go in the EnvelopeDecoder companion object, but since that's only a type alias,
    // we need to put it here for the compiler to find it during implicit search
    implicit def stringDecoder[F[_]: ApplicativeThrow]: EnvelopeDecoder[F, String] =
      (EnvelopeDecoder.payload[F], encoding[F]).mapN((p, e) => new String(p, e.getOrElse(UTF_8)))

    implicit val amqpEnvelopeTraverse: Traverse[AmqpEnvelope] = new Traverse[AmqpEnvelope] {
      override def traverse[G[_]: Applicative, A, B](fa: AmqpEnvelope[A])(f: A => G[B]): G[AmqpEnvelope[B]] =
        f(fa.payload).map(b => fa.copy(payload = b))

      override def foldLeft[A, B](fa: AmqpEnvelope[A], b: B)(f: (B, A) => B): B =
        f(b, fa.payload)

      override def foldRight[A, B](fa: AmqpEnvelope[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
        f(fa.payload, lb)
    }

    implicit def eqAmqpEnvelope[A](implicit A: Eq[A]): Eq[AmqpEnvelope[A]] =
      Eq.and(
        Eq.by(_.payload),
        Eq.and(
          Eq.by(_.deliveryTag),
          Eq.and(
            Eq.by(_.properties),
            Eq.and(
              Eq.by(_.exchangeName),
              Eq.and(Eq.by(_.routingKey), Eq.by(_.redelivered))
            )
          )
        )
      )
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

  val instantOrderWithSecondPrecision: Order[Instant] = Order.by(_.getEpochSecond)
}
