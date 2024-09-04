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

import cats.Functor
import cats.data.{NonEmptyList, NonEmptySeq}
import cats.syntax.all._
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.ByteArrayVal
import dev.profunktor.fs2rabbit.model.codec.AmqpFieldDecoder.DecodingError
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, ShortString}

import java.time.Instant
import java.util.Date
import scala.reflect.ClassTag

trait AmqpFieldDecoder[T] { $this =>

  def decode(value: AmqpFieldValue): Either[DecodingError, T]

  def option: AmqpFieldDecoder[Option[T]] =
    either.map(_.toOption)

  def either: AmqpFieldDecoder[Either[DecodingError, T]] =
    AmqpFieldDecoder.instance(value => decode(value).asRight)

  final def map[U](f: T => U): AmqpFieldDecoder[U] =
    AmqpFieldDecoder.instance(t => $this.decode(t).map(f))

  final def emap[U](f: T => Either[DecodingError, U]): AmqpFieldDecoder[U] =
    AmqpFieldDecoder.instance(t => $this.decode(t).flatMap(f))
}
object AmqpFieldDecoder extends AmqpFieldDecoderInstances {
  case class DecodingError(msg: String, cause: Option[Throwable] = None) extends Throwable(msg, cause.orNull)
  object DecodingError {
    def expectedButGot(expected: String, got: String): DecodingError =
      new DecodingError(s"Expected $expected, but got $got")
  }

  def apply[T: AmqpFieldDecoder]: AmqpFieldDecoder[T] = implicitly[AmqpFieldDecoder[T]]

  def instance[T](decoder: AmqpFieldValue => Either[DecodingError, T]): AmqpFieldDecoder[T] =
    (value: AmqpFieldValue) => decoder(value)

  implicit val functorInstance: Functor[AmqpFieldDecoder] = new Functor[AmqpFieldDecoder] {
    def map[A, B](fa: AmqpFieldDecoder[A])(f: A => B): AmqpFieldDecoder[B] = fa.map(f)
  }
}
sealed trait AmqpFieldDecoderInstances {

  implicit def amqpFieldValueDecoder[T <: AmqpFieldValue]: AmqpFieldDecoder[T] =
    AmqpFieldDecoder.instance(v =>
      Either.catchNonFatal(v.asInstanceOf[T]).leftMap(err => DecodingError("Error decoding AmqpFieldValue", Some(err)))
    )

  implicit val anyDecoder: AmqpFieldDecoder[Any] =
    AmqpFieldDecoder.instance(_.asRight)

  implicit val unitDecoder: AmqpFieldDecoder[Unit] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.NullVal => Right(())
      case other                  => DecodingError.expectedButGot("NullVal", other.toString).asLeft
    }

  implicit val stringDecoder: AmqpFieldDecoder[String] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.StringVal(value) => value.asRight
      case other                           => DecodingError.expectedButGot("StringVal", other.toString).asLeft
    }

  implicit val instantDecoder: AmqpFieldDecoder[Instant] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.TimestampVal(value) => value.asRight
      case other                              => DecodingError.expectedButGot("TimestampVal", other.toString).asLeft
    }

  implicit val dateDecoder: AmqpFieldDecoder[Date] =
    instantDecoder.emap { instant =>
      Either
        .catchNonFatal(
          Date.from(instant)
        )
        .leftMap(err => DecodingError("Error decoding Date", Some(err)))
    }

  implicit val booleanDecoder: AmqpFieldDecoder[Boolean] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.BooleanVal(value) => value.asRight
      case other                            => DecodingError.expectedButGot("BooleanVal", other.toString).asLeft
    }

  implicit val byteDecoder: AmqpFieldDecoder[Byte] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ByteVal(value) => value.asRight
      case other                         => DecodingError.expectedButGot("ByteVal", other.toString).asLeft
    }

  implicit val shortDecoder: AmqpFieldDecoder[Short] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ShortVal(value) => value.asRight
      case other                          => DecodingError.expectedButGot("ShortVal", other.toString).asLeft
    }

  implicit val intDecoder: AmqpFieldDecoder[Int] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ByteVal(value)  => value.toInt.asRight
      case AmqpFieldValue.ShortVal(value) => value.toInt.asRight
      case AmqpFieldValue.IntVal(value)   => value.asRight
      case other                          => DecodingError.expectedButGot("IntVal", other.toString).asLeft
    }

  implicit val longDecoder: AmqpFieldDecoder[Long] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ByteVal(value)  => value.toLong.asRight
      case AmqpFieldValue.ShortVal(value) => value.toLong.asRight
      case AmqpFieldValue.IntVal(value)   => value.toLong.asRight
      case AmqpFieldValue.LongVal(value)  => value.asRight
      case other                          => DecodingError.expectedButGot("LongVal", other.toString).asLeft
    }

  implicit val floatDecoder: AmqpFieldDecoder[Float] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ByteVal(value)  => value.toFloat.asRight
      case AmqpFieldValue.ShortVal(value) => value.toFloat.asRight
      case AmqpFieldValue.IntVal(value)   => value.toFloat.asRight
      case AmqpFieldValue.LongVal(value)  => value.toFloat.asRight
      case AmqpFieldValue.FloatVal(value) => value.asRight
      case other                          => DecodingError.expectedButGot("FloatVal", other.toString).asLeft
    }

  implicit val doubleDecoder: AmqpFieldDecoder[Double] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ByteVal(value)   => value.toDouble.asRight
      case AmqpFieldValue.ShortVal(value)  => value.toDouble.asRight
      case AmqpFieldValue.IntVal(value)    => value.toDouble.asRight
      case AmqpFieldValue.LongVal(value)   => value.toDouble.asRight
      case AmqpFieldValue.FloatVal(value)  => value.toDouble.asRight
      case AmqpFieldValue.DoubleVal(value) => value.asRight
      case other                           => DecodingError.expectedButGot("DoubleVal", other.toString).asLeft
    }

  implicit val bigDecimalDecoder: AmqpFieldDecoder[BigDecimal] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ByteVal(value)    => BigDecimal(value.toInt).asRight
      case AmqpFieldValue.ShortVal(value)   => BigDecimal(value.toInt).asRight
      case AmqpFieldValue.IntVal(value)     => BigDecimal(value).asRight
      case AmqpFieldValue.LongVal(value)    => BigDecimal(value).asRight
      case AmqpFieldValue.FloatVal(value)   => BigDecimal(value.toDouble).asRight
      case AmqpFieldValue.DoubleVal(value)  => BigDecimal(value).asRight
      case AmqpFieldValue.DecimalVal(value) => value.asRight
      case other                            => DecodingError.expectedButGot("DecimalVal", other.toString).asLeft
    }

  implicit val bigIntDecoder: AmqpFieldDecoder[BigInt] =
    bigDecimalDecoder.map(_.toBigInt)

  // collections
  implicit def mapDecoder: AmqpFieldDecoder[Map[ShortString, AmqpFieldValue]] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.TableVal(value) => value.asRight
      case other                          => DecodingError.expectedButGot("TableVal", other.toString).asLeft
    }

  implicit def optionDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[Option[T]] =
    AmqpFieldDecoder[T].option

  implicit def eitherDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[Either[DecodingError, T]] =
    AmqpFieldDecoder[T].either

  implicit def arrayDecoder[T: AmqpFieldDecoder: ClassTag]: AmqpFieldDecoder[Array[T]] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ArrayVal(value)                                  =>
        value.traverse(AmqpFieldDecoder[T].decode).map(_.toArray[T])
      case ByteArrayVal(value) if implicitly[ClassTag[T]] == ClassTag.Byte =>
        value.toArray.asInstanceOf[Array[T]].asRight
      case other                                                           =>
        DecodingError.expectedButGot("ArrayVal", other.toString).asLeft
    }

  implicit def collectionSeqDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[collection.Seq[T]] =
    AmqpFieldDecoder.instance {
      case AmqpFieldValue.ArrayVal(values) => values.traverse(AmqpFieldDecoder[T].decode)
      case other                           => DecodingError.expectedButGot(s"ArrayVal", other.toString).asLeft
    }

  implicit def seqDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[Seq[T]] =
    collectionSeqDecoder[T].map(_.toSeq)

  implicit def listDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[List[T]] =
    collectionSeqDecoder[T].map(_.toList)

  implicit def setDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[Set[T]] =
    collectionSeqDecoder[T].map(_.toSet)

  // cats collections
  implicit def nelDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[NonEmptyList[T]] =
    listDecoder[T].emap(ls =>
      NonEmptyList.fromList(ls).toRight(DecodingError.expectedButGot("NonEmptyList", "empty list"))
    )

  implicit def nesDecoder[T: AmqpFieldDecoder]: AmqpFieldDecoder[NonEmptySeq[T]] =
    seqDecoder[T].emap((seq: Seq[T]) =>
      NonEmptySeq.fromSeq(seq.toList).toRight(DecodingError.expectedButGot("NonEmptySeq", "empty seq"))
    )
}
