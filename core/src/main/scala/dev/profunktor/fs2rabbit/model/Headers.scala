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

package dev.profunktor.fs2rabbit.model

import cats.syntax.all.*
import cats.{ApplicativeThrow, Eq, Show}
import dev.profunktor.fs2rabbit.model.Headers.MissingHeader
import dev.profunktor.fs2rabbit.model.codec.{AmqpFieldDecoder, AmqpFieldEncoder}

import scala.util.Try

/** * Represents AMQP headers.
  *
  * Values are represented by `AmqpFieldValue` which is a sealed trait with the following implementations:
  *   - `StringVal`
  *   - `BooleanVal`
  *   - `ByteVal`
  *   - `ShortVal`
  *   - `IntVal`
  *   - `LongVal`
  *   - `FloatVal`
  *   - `DoubleVal`
  *   - `DecimalVal`
  *   - `TimestampVal`
  *   - `ArrayVal`
  *   - `TableVal`
  *   - `NullVal`
  *
  * To facilitate the usage read and write to this class work with implicit `AmqpFieldEncoder` and `AmqpFieldDecoder`.
  * You can use the `:=[T, AmqpFieldValue]` operator to encode your types into a tuple of `HeaderKey` and
  * `AmqpFieldValue`.
  *
  * Methods like `getAs, `getOptAs`` and `getOptAsF` are provided to decode the values back to your types.
  *
  * ==Example==
  * {{{
  *   import dev.profunktor.fs2rabbit.model.Headers
  *   import dev.profunktor.fs2rabbit.model.Headers._
  *
  *   val headers = Headers(
  *    "x-custom-header" := "value", // StringVal
  *    "x-custom-header-2" := 123 // IntVal
  *   )
  *     .updated("x-custom-header-3", 123L) // LongVal
  *     .remove("x-custom-header-3")
  *   /*
  *   * OR
  *   * val headers = Headers(
  *   *    "x-custom-header" -> StringVal("value"),
  *   *    "x-custom-header-2" -> IntVal(123)
  *   *   )
  *   *   .updated("x-custom-header-3", LongVal(123L))
  *   *   .remove("x-custom-header-3")
  *   */
  *
  *   headers.get[IO]("x-custom-header") // IO(StringVal("value"))
  *   headers.getOpt("x-custom-header") // Some(StringVal("value"))
  *
  *   headers.getAs[IO, String]("x-custom-header") // IO("value")
  *   headers.getOptAs[String]("x-custom-header") // Some("value")
  *   headers.getOptAsF[IO, Int]("x-custom-header-2") // IO(Some(123))
  * }}}
  */
case class Headers(toMap: Map[HeaderKey, AmqpFieldValue]) {

  def exists(p: ((HeaderKey, AmqpFieldValue)) => Boolean): Boolean =
    toMap.exists(p)

  def contains(key: HeaderKey): Boolean =
    toMap.contains(key)

  // add
  def +(kv: (HeaderKey, AmqpFieldValue)): Headers =
    updated(kv._1, kv._2)

  def updated[T: AmqpFieldEncoder](key: HeaderKey, value: T): Headers =
    Headers(toMap.updated(key, AmqpFieldEncoder[T].encode(value)))

  def ++(that: Headers): Headers =
    Headers(toMap ++ that.toMap)

  // remove
  def remove(key: HeaderKey, keys: HeaderKey*): Headers =
    Headers(toMap -- (key +: keys))

  def -(key: HeaderKey): Headers =
    remove(key)

  def --(that: Headers): Headers =
    Headers(toMap -- that.toMap.keys)

  // as
  /** Decodes the value of the mandatory header to the specified type `T`.
    *   - If the header is missing, it will raise a `MissingHeader` error.
    *   - If the decoding fails, it will raise a `DecodingError` error.
    * @param name
    *   the name of the header
    */
  def getAs[F[_], T: AmqpFieldDecoder](name: HeaderKey)(implicit F: ApplicativeThrow[F]): F[T] =
    F.fromEither(get[Either[Throwable, *]](name).flatMap(_.as[T]))

  /** Decodes the value of the optional header to the specified type `T`.
    *   - If the header is missing, it will return `None`.
    *   - If the decoding fails, it will return `None`.
    *
    * Check the `getOptAsF` method if you want to handle decoding errors in a different effect.
    *
    * @param name
    *   the name of the header
    */
  def getOptAs[T: AmqpFieldDecoder](name: HeaderKey): Option[T] =
    getOptAsF[Try, T](name).toOption.flatten

  /** Decodes the value of the optional header to the specified type `T`.
    *   - If the header is missing, it will return `F.pure[None]`.
    *   - If the decoding fails, it will raise a `DecodingError`.
    * @param name
    *   the name of the header
    */
  def getOptAsF[F[_], T: AmqpFieldDecoder](name: HeaderKey)(implicit F: ApplicativeThrow[F]): F[Option[T]] =
    F.fromEither(getOpt(name).map(_.as[T]).sequence)

  // raw
  /** Returns the raw value of the mandatory header.
    *   - If the header is missing, it will raise a `MissingHeader` error.
    * @param name
    *   the name of the header
    */
  def get[F[_]](name: HeaderKey)(implicit F: ApplicativeThrow[F]): F[AmqpFieldValue] =
    getOpt(name) match {
      case Some(value) => F.pure(value)
      case None        => F.raiseError(MissingHeader(name))
    }

  /** Returns the raw optional value of the header.
    *   - If the header is missing, it will return `None`.
    * @param name
    *   the name of the header
    */
  def getOpt(name: HeaderKey): Option[AmqpFieldValue] =
    toMap.get(name)

  override def toString: String =
    Headers.show.show(this)

  override final def equals(obj: Any): Boolean =
    obj match {
      case that: Headers => Headers.eq.eqv(this, that)
      case _             => false
    }
}
object Headers {

  val empty: Headers                                         = new Headers(Map.empty)
  def apply(values: (HeaderKey, AmqpFieldValue)*): Headers   = Headers(values.toMap)
  def apply(values: Map[HeaderKey, AmqpFieldValue]): Headers = new Headers(values)

  case class MissingHeader(name: String) extends RuntimeException(s"Missing header: $name")

  private[fs2rabbit] def unsafeFromMap(values: Map[String, AnyRef]): Headers =
    Headers(values.map { case (k, v) => k -> AmqpFieldValue.unsafeFrom(v) })

  // instances
  implicit val show: Show[Headers] = Show.show(h => s"Headers(${h.toMap.mkString(", ")})")
  implicit val eq: Eq[Headers]     = Eq.by(_.toMap)
}
