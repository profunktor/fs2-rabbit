/*
 * Copyright 2017-2025 ProfunKtor
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

package dev.profunktor.fs2rabbit.effects
import cats.data.Kleisli
import cats.implicits._
import cats.{Applicative, ApplicativeError, ApplicativeThrow}
import dev.profunktor.fs2rabbit.model.codec.AmqpFieldDecoder
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, AmqpProperties, ExchangeName, HeaderKey, Headers, RoutingKey}

object EnvelopeDecoder extends EnvelopeDecoderInstances {

  def apply[F[_], A](implicit e: EnvelopeDecoder[F, A]): EnvelopeDecoder[F, A] = e

  def properties[F[_]: Applicative]: EnvelopeDecoder[F, AmqpProperties] =
    Kleisli(e => e.properties.pure[F])

  def payload[F[_]: Applicative]: EnvelopeDecoder[F, Array[Byte]] =
    Kleisli(_.payload.pure[F])

  def routingKey[F[_]: Applicative]: EnvelopeDecoder[F, RoutingKey] =
    Kleisli(e => e.routingKey.pure[F])

  def exchangeName[F[_]: Applicative]: EnvelopeDecoder[F, ExchangeName] =
    Kleisli(e => e.exchangeName.pure[F])

  def redelivered[F[_]: Applicative]: EnvelopeDecoder[F, Boolean] =
    Kleisli(e => e.redelivered.pure[F])

  // header
  def headers[F[_]: ApplicativeThrow]: EnvelopeDecoder[F, Headers] =
    Kleisli(_.properties.headers.pure[F])

  def header[F[_]: ApplicativeThrow](key: String): EnvelopeDecoder[F, AmqpFieldValue] =
    Kleisli(_.properties.headers.get[F](key))

  def headerAs[F[_]: ApplicativeThrow, T: AmqpFieldDecoder](key: HeaderKey): EnvelopeDecoder[F, T] =
    Kleisli(_.properties.headers.getAs[F, T](key))

  def optHeader[F[_]: Applicative](key: HeaderKey): EnvelopeDecoder[F, Option[AmqpFieldValue]] =
    Kleisli(_.properties.headers.getOpt(key).pure[F])

  def optHeaderAs[F[_]: ApplicativeThrow, T: AmqpFieldDecoder](key: HeaderKey): EnvelopeDecoder[F, Option[T]] =
    Kleisli(_.properties.headers.getOptAsF[F, T](key))

  @deprecated("Use headerAs[F, String] instead", "5.3.0")
  def stringHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, String] =
    headerAs[F, String](name)

  @deprecated("Use headerAs[F, Int] instead", "5.3.0")
  def intHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Int] =
    headerAs[F, Int](name)

  @deprecated("Use headerAs[F, Long] instead", "5.3.0")
  def longHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Long] =
    headerAs[F, Long](name)

  @deprecated("Use optHeaderAs[F, String] instead", "5.3.0")
  def optStringHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[String]] =
    optHeaderAs[F, String](name)

  @deprecated("Use optHeaderAs[F, Int] instead", "5.3.0")
  def optIntHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[Int]] =
    optHeaderAs[F, Int](name)

  @deprecated("Use optHeaderAs[F, Long] instead", "5.3.0")
  def optLongHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[Long]] =
    optHeaderAs[F, Long](name)

  @deprecated("Use headerAs[F, collection.Seq[Any]] instead", "5.3.0")
  def arrayHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, collection.Seq[Any]] =
    headerAs[F, collection.Seq[Any]](name)(ApplicativeThrow[F], AmqpFieldDecoder.collectionSeqDecoder[Any])

  @deprecated("Use optHeaderAs[F, collection.Seq[Any]] instead", "5.3.0")
  def optArrayHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[collection.Seq[Any]]] =
    optHeaderAs[F, collection.Seq[Any]](name)(ApplicativeThrow[F], AmqpFieldDecoder.collectionSeqDecoder[Any])
}

sealed trait EnvelopeDecoderInstances {

  implicit def decoderAttempt[F[_], E: ApplicativeError[F, *], A](implicit
      decoder: EnvelopeDecoder[F, A]
  ): EnvelopeDecoder[F, Either[E, A]] =
    decoder.attempt

  implicit def decoderOption[F[_], E: ApplicativeError[F, *], A](implicit
      decoder: EnvelopeDecoder[F, A]
  ): EnvelopeDecoder[F, Option[A]] =
    decoder.attempt.map(_.toOption)
}
