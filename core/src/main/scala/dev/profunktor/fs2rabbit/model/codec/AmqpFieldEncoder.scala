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

package dev.profunktor.fs2rabbit.model.codec

import cats.Contravariant
import cats.data.{NonEmptyList, NonEmptySeq, NonEmptySet}
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{ArrayVal, DecimalVal}
import dev.profunktor.fs2rabbit.model.codec.AmqpFieldDecoder.DecodingError
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, ShortString}
import scodec.bits.ByteVector

import java.time.Instant
import java.util.Date

trait AmqpFieldEncoder[T] {
  def encode(value: T): AmqpFieldValue
  final def contramap[U](f: U => T): AmqpFieldEncoder[U] =
    AmqpFieldEncoder.instance(f.andThen(encode))
}

object AmqpFieldEncoder extends FieldEncoderInstances {
  def apply[T](implicit encoder: AmqpFieldEncoder[T]): AmqpFieldEncoder[T] =
    encoder

  def instance[T](f: T => AmqpFieldValue): AmqpFieldEncoder[T] =
    (value: T) => f(value)

  implicit val contravariantInstance: Contravariant[AmqpFieldEncoder] = new Contravariant[AmqpFieldEncoder] {
    def contramap[A, B](fa: AmqpFieldEncoder[A])(f: B => A): AmqpFieldEncoder[B] = fa.contramap(f)
  }
}
sealed trait FieldEncoderInstances {

  implicit def amqpFieldValueEncoder[A <: AmqpFieldValue]: AmqpFieldEncoder[A] =
    AmqpFieldEncoder.instance(identity)

  implicit val unitEncoder: AmqpFieldEncoder[Unit] =
    (_: Unit) => AmqpFieldValue.NullVal

  implicit val nullEncoder: AmqpFieldEncoder[Null] =
    (_: Null) => AmqpFieldValue.NullVal

  implicit val stringEncoder: AmqpFieldEncoder[String] =
    (value: String) => AmqpFieldValue.StringVal(value)

  implicit val instantEncoder: AmqpFieldEncoder[Instant] =
    (value: Instant) => AmqpFieldValue.TimestampVal.from(value)

  implicit val dateEncoder: AmqpFieldEncoder[Date] =
    (value: Date) => AmqpFieldValue.TimestampVal.from(value)

  implicit val booleanEncoder: AmqpFieldEncoder[Boolean] =
    (value: Boolean) => AmqpFieldValue.BooleanVal(value)

  implicit val byteEncoder: AmqpFieldEncoder[Byte] =
    (value: Byte) => AmqpFieldValue.ByteVal(value)

  implicit val shortEncoder: AmqpFieldEncoder[Short] =
    (value: Short) => AmqpFieldValue.ShortVal(value)

  implicit val intEncoder: AmqpFieldEncoder[Int] =
    (value: Int) => AmqpFieldValue.IntVal(value)

  implicit val longEncoder: AmqpFieldEncoder[Long] =
    (value: Long) => AmqpFieldValue.LongVal(value)

  implicit val floatEncoder: AmqpFieldEncoder[Float] =
    (value: Float) => AmqpFieldValue.FloatVal(value)

  implicit val doubleEncoder: AmqpFieldEncoder[Double] =
    (value: Double) => AmqpFieldValue.DoubleVal(value)

  implicit val bigDecimalEncoder: AmqpFieldEncoder[BigDecimal] =
    (value: BigDecimal) =>
      AmqpFieldValue.DecimalVal.from(value) match {
        case Some(decimalVal: DecimalVal) => decimalVal
        case None                         => AmqpFieldValue.NullVal
      }

  implicit val bigIntEncoder: AmqpFieldEncoder[BigInt] =
    bigDecimalEncoder.contramap(BigDecimal(_))

  implicit val decodingErrorEncoder: AmqpFieldEncoder[DecodingError] =
    stringEncoder.contramap(_.getMessage)

  // collections
  implicit val mapEncoder: AmqpFieldEncoder[Map[ShortString, AmqpFieldValue]] =
    (value: Map[ShortString, AmqpFieldValue]) => AmqpFieldValue.TableVal(value)

  implicit def optionEncoder[T: AmqpFieldEncoder]: AmqpFieldEncoder[Option[T]] = {
    case Some(v) => AmqpFieldEncoder[T].encode(v)
    case None    => AmqpFieldValue.NullVal
  }

  implicit def eitherEncoder[L: AmqpFieldEncoder, R: AmqpFieldEncoder]: AmqpFieldEncoder[Either[L, R]] = {
    case Left(l)  => AmqpFieldEncoder[L].encode(l)
    case Right(r) => AmqpFieldEncoder[R].encode(r)
  }

  implicit val byteVectorEncoder: AmqpFieldEncoder[ByteVector] =
    (value: ByteVector) => AmqpFieldValue.ByteArrayVal(value)

  implicit val byteArrayEncoder: AmqpFieldEncoder[Array[Byte]] =
    byteVectorEncoder.contramap(ByteVector(_))

  implicit def arrayEncoder[T: AmqpFieldEncoder]: AmqpFieldEncoder[Array[T]] =
    AmqpFieldEncoder.instance[Array[T]](v => ArrayVal(v.map(AmqpFieldEncoder[T].encode(_)).toVector))

  implicit def seqFieldEncoder[S[X] <: Seq[X], T: AmqpFieldEncoder]: AmqpFieldEncoder[S[T]] =
    AmqpFieldEncoder.instance[S[T]](v => ArrayVal(v.map(AmqpFieldEncoder[T].encode(_)).toVector))

  implicit def setFieldEncoder[T: AmqpFieldEncoder]: AmqpFieldEncoder[Set[T]] =
    AmqpFieldEncoder.instance[Set[T]](v => ArrayVal(v.map(AmqpFieldEncoder[T].encode(_)).toVector))

  // cats collections
  implicit def nelFieldEncoder[T: AmqpFieldEncoder]: AmqpFieldEncoder[NonEmptyList[T]] =
    seqFieldEncoder[List, T].contramap(_.toList)

  implicit def nesFieldEncoder[T: AmqpFieldEncoder]: AmqpFieldEncoder[NonEmptySeq[T]] =
    seqFieldEncoder[Seq, T].contramap(_.toSeq)

  implicit def neSortedSetFieldEncoder[T: AmqpFieldEncoder]: AmqpFieldEncoder[NonEmptySet[T]] =
    seqFieldEncoder[List, T].contramap(_.toSortedSet.toList)
}
