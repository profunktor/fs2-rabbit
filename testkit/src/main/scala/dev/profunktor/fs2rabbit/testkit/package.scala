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

import java.time.Instant
import java.util.Date

import dev.profunktor.fs2rabbit.model.AmqpFieldValue._
import dev.profunktor.fs2rabbit.model._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalacheck.rng.Seed
import scodec.bits.ByteVector

package object testkit {
  implicit def arbAmqpEnvelope[A](implicit A: Arbitrary[A]): Arbitrary[AmqpEnvelope[A]] = Arbitrary {
    for {
      deliveryTag <- arbitrary[DeliveryTag]
      payload     <- arbitrary[A]
      props       <- arbitrary[AmqpProperties]
      exchange    <- arbitrary[ExchangeName]
      routingKey  <- arbitrary[RoutingKey]
      redelivered <- arbitrary[Boolean]
    } yield AmqpEnvelope(deliveryTag, payload, props, exchange, routingKey, redelivered)
  }
  implicit def cogenAmqpEnvelope[A: Cogen]: Cogen[AmqpEnvelope[A]]                      =
    Cogen[(DeliveryTag, A, ExchangeName, RoutingKey, Boolean)].contramap { a =>
      (a.deliveryTag, a.payload, a.exchangeName, a.routingKey, a.redelivered)
    }

  implicit val arbAmqpProperties: Arbitrary[AmqpProperties] = Arbitrary {
    for {
      contentType     <- Gen.option(Gen.const("application/json"))
      contentEncoding <- Gen.option(Gen.const("UTF-8"))
      priority        <- Gen.option(arbitrary[Int])
      deliveryMode    <- Gen.option(arbitrary[DeliveryMode])
      correlationId   <- Gen.option(Gen.identifier)
      messageId       <- Gen.option(Gen.identifier)
      tpe             <- Gen.option(arbitrary[String])
      userId          <- Gen.option(Gen.identifier)
      appId           <- Gen.option(Gen.identifier)
      expiration      <- Gen.option(arbitrary[String])
      replyTo         <- Gen.option(Gen.identifier)
      clusterId       <- Gen.option(Gen.identifier)
      timestamp       <- Gen.option(arbitrary[Instant])
      headers         <- arbitrary[Map[String, AmqpFieldValue]]
    } yield AmqpProperties(
      contentType = contentType,
      contentEncoding = contentEncoding,
      priority = priority,
      deliveryMode = deliveryMode,
      correlationId = correlationId,
      messageId = messageId,
      `type` = tpe,
      userId = userId,
      appId = appId,
      expiration = expiration,
      replyTo = replyTo,
      clusterId = clusterId,
      timestamp = timestamp,
      headers = headers
    )
  }
  implicit val cogenAmqpProperties: Cogen[AmqpProperties]   =
    Cogen.it { p =>
      (p.contentType ++ p.contentEncoding ++ p.correlationId ++ p.messageId ++ p.userId ++ p.appId).iterator
    }

  implicit val arbDeliveryTag: Arbitrary[DeliveryTag] = Arbitrary(Gen.chooseNum(1L, Long.MaxValue).map(DeliveryTag(_)))
  implicit val cogenDeliveryTag: Cogen[DeliveryTag]   = Cogen[Long].contramap(_.value)

  implicit val arbExchangeName: Arbitrary[ExchangeName] = Arbitrary(Gen.asciiPrintableStr.map(ExchangeName(_)))
  implicit val cogenExchangeName: Cogen[ExchangeName]   = Cogen[String].contramap(_.value)

  implicit val arbRoutingKey: Arbitrary[RoutingKey] = Arbitrary(Gen.asciiPrintableStr.map(RoutingKey(_)))
  implicit val cogenRoutingKey: Cogen[RoutingKey]   = Cogen[String].contramap(_.value)

  implicit val arbQueueName: Arbitrary[QueueName] = Arbitrary(Gen.asciiPrintableStr.map(QueueName(_)))
  implicit val cogenQueueName: Cogen[QueueName]   = Cogen[String].contramap(_.value)

  implicit val arbConsumerTag: Arbitrary[ConsumerTag] = Arbitrary(Gen.asciiPrintableStr.map(ConsumerTag(_)))
  implicit val cogenConsumerTag: Cogen[ConsumerTag]   = Cogen[String].contramap(_.value)

  implicit val arbShortString: Arbitrary[ShortString] = Arbitrary {
    Gen
      .listOfN(ShortString.MaxByteLength / 3, arbitrary[Char])
      .map(_.mkString)
      .suchThat { str =>
        ShortString.from(str).isDefined
      }
      .map(ShortString.unsafeFrom)
  }
  implicit val cogenShortString: Cogen[ShortString]   = Cogen[String].contramap(_.str)

  implicit val arbTableVal: Arbitrary[TableVal] = Arbitrary {
    Gen.lzy(arbitrary[Map[ShortString, AmqpFieldValue]].map(TableVal(_)))
  }
  implicit val cogenTableVal: Cogen[TableVal]   = Cogen[Map[ShortString, AmqpFieldValue]].contramap(_.value)

  implicit val arbByteArrayVal: Arbitrary[ByteArrayVal] = Arbitrary(
    arbitrary[Array[Byte]].map(ByteVector(_)).map(ByteArrayVal(_))
  )
  implicit val cogenByteArrayVal: Cogen[ByteArrayVal]   = Cogen[Array[Byte]].contramap(_.value.toArray)

  implicit val arbArrayVal: Arbitrary[ArrayVal] = Arbitrary {
    Gen.lzy(arbitrary[Vector[AmqpFieldValue]].map(ArrayVal(_)))
  }
  implicit val cogenArrayVal: Cogen[ArrayVal]   = Cogen[Vector[AmqpFieldValue]].contramap(_.value)

  implicit val arbDeliveryMode: Arbitrary[DeliveryMode] = Arbitrary(
    Gen.oneOf(DeliveryMode.NonPersistent, DeliveryMode.Persistent)
  )
  implicit val cogenDeliveryMode: Cogen[DeliveryMode]   = Cogen[Int].contramap(_.value)

  implicit val arbTimestampVal: Arbitrary[TimestampVal] = Arbitrary(arbitrary[Date].map(TimestampVal.from))
  implicit val cogenTimestampVal: Cogen[TimestampVal]   =
    Cogen[Long].contramap(_.instantWithOneSecondAccuracy.getEpochSecond)

  implicit val arbDecimalVal: Arbitrary[DecimalVal] = Arbitrary {
    val safeBigDecimalGen: Gen[BigDecimal] =
      for {
        unscaled <- arbitrary[Int]
        scale    <- Gen.chooseNum(0, 255)
      } yield BigDecimal(BigInt(unscaled), scale)
    safeBigDecimalGen.suchThat(DecimalVal.from(_).isDefined).map(DecimalVal.unsafeFrom)
  }
  implicit val cogenDecimalVal: Cogen[DecimalVal]   = Cogen[BigDecimal].contramap(_.sizeLimitedBigDecimal)

  implicit val arbByteVal: Arbitrary[ByteVal] = Arbitrary(arbitrary[Byte].map(ByteVal(_)))
  implicit val cogenByteVal: Cogen[ByteVal]   = Cogen[Byte].contramap(_.value)

  implicit val arbDoubleVal: Arbitrary[DoubleVal] = Arbitrary(arbitrary[Double].map(DoubleVal(_)))
  implicit val cogenDoubleVal: Cogen[DoubleVal]   = Cogen[Double].contramap(_.value)

  implicit val arbFloatVal: Arbitrary[FloatVal] = Arbitrary(arbitrary[Float].map(FloatVal(_)))
  implicit val cogenFloatVal: Cogen[FloatVal]   = Cogen[Float].contramap(_.value)

  implicit val arbShortVal: Arbitrary[ShortVal] = Arbitrary(arbitrary[Short].map(ShortVal(_)))
  implicit val cogenShortVal: Cogen[ShortVal]   = Cogen[Short].contramap(_.value)

  implicit val arbBooleanVal: Arbitrary[BooleanVal] = Arbitrary(arbitrary[Boolean].map(BooleanVal(_)))
  implicit val cogenBooleanVal: Cogen[BooleanVal]   = Cogen[Boolean].contramap(_.value)

  implicit val arbIntVal: Arbitrary[IntVal] = Arbitrary(arbitrary[Int].map(IntVal(_)))
  implicit val cogenIntVal: Cogen[IntVal]   = Cogen[Int].contramap(_.value)

  implicit val arbLongVal: Arbitrary[LongVal] = Arbitrary(arbitrary[Long].map(LongVal(_)))
  implicit val cogenLongVal: Cogen[LongVal]   = Cogen[Long].contramap(_.value)

  implicit val arbStringVal: Arbitrary[StringVal] = Arbitrary(arbitrary[String].map(StringVal(_)))
  implicit val cogenStringVal: Cogen[StringVal]   = Cogen[String].contramap(_.value)

  implicit val arbNullVal: Arbitrary[NullVal.type] = Arbitrary(Gen.const(NullVal))
  implicit val cogenNullVal: Cogen[NullVal.type]   = Cogen[Long].contramap(_ => 0L)

  implicit val arbAmqpFieldValue: Arbitrary[AmqpFieldValue] = Arbitrary {
    Gen.sized { size =>
      val effectiveSize = size min 5
      Gen.resize(
        0 max (effectiveSize - 1),
        Gen.lzy {
          Gen.oneOf(
            arbTableVal.arbitrary,
            arbByteArrayVal.arbitrary,
            arbArrayVal.arbitrary,
            arbTimestampVal.arbitrary,
            arbDecimalVal.arbitrary,
            arbByteVal.arbitrary,
            arbDoubleVal.arbitrary,
            arbFloatVal.arbitrary,
            arbShortVal.arbitrary,
            arbBooleanVal.arbitrary,
            arbIntVal.arbitrary,
            arbLongVal.arbitrary,
            arbStringVal.arbitrary,
            arbNullVal.arbitrary
          )
        }
      )
    }
  }

  implicit def cogenAmqpFieldValue: Cogen[AmqpFieldValue] =
    Cogen { (seed: Seed, t: AmqpFieldValue) =>
      t match {
        case a: TableVal     => Cogen[TableVal].perturb(seed, a)
        case a: ByteArrayVal => Cogen[ByteArrayVal].perturb(seed, a)
        case a: ArrayVal     => Cogen[ArrayVal].perturb(seed, a)
        case a: TimestampVal => Cogen[TimestampVal].perturb(seed, a)
        case a: DecimalVal   => Cogen[DecimalVal].perturb(seed, a)
        case a: ByteVal      => Cogen[ByteVal].perturb(seed, a)
        case a: DoubleVal    => Cogen[DoubleVal].perturb(seed, a)
        case a: FloatVal     => Cogen[FloatVal].perturb(seed, a)
        case a: ShortVal     => Cogen[ShortVal].perturb(seed, a)
        case a: BooleanVal   => Cogen[BooleanVal].perturb(seed, a)
        case a: IntVal       => Cogen[IntVal].perturb(seed, a)
        case a: LongVal      => Cogen[LongVal].perturb(seed, a)
        case a: StringVal    => Cogen[StringVal].perturb(seed, a)
        case a: NullVal.type => Cogen[NullVal.type].perturb(seed, a)
      }
    }
}
