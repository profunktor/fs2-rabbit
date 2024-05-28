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

import cats.syntax.all._
import cats.{ApplicativeThrow, Eq}
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
  *   import dev.profunktor.fs2rabbit.data.Headers
  *   import dev.profunktor.fs2rabbit.data.Headers._
  *
  *   val headers = Headers(
  *    "x-custom-header" := "value", // StringVal
  *    "x-custom-header-2" := 123 // IntVal
  *   )
  *     .append("x-custom-header-3", 123L) // LongVal
  *     .remove("x-custom-header-3")
  *   /*
  *   * OR
  *   * val headers = Headers(
  *   *    "x-custom-header" -> StringVal("value"),
  *   *    "x-custom-header-2" -> IntVal(123)
  *   *   )
  *   *   .append("x-custom-header-3", LongVal(123L))
  *   *   .remove("x-custom-header-3")
  *   */
  *
  *   headers.getRaw[IO]("x-custom-header") // IO(StringVal("value"))
  *   headers.getOptRaw("x-custom-header") // Some(StringVal("value"))
  *
  *   headers.getAs[IO, String]("x-custom-header") // IO("value")
  *   headers.getOptAs[String]("x-custom-header") // Some("value")
  *   headers.getOptAsF[IO, Int]("x-custom-header-2") // IO(Some(123))
  * }}}
  */
case class Headers(toMap: Map[HeaderKey, AmqpFieldValue]) {

  // append
  def :+[T: AmqpFieldEncoder](kv: (HeaderKey, T)): Headers =
    append(kv._1, kv._2)

  def append[T: AmqpFieldEncoder](key: HeaderKey, value: T): Headers =
    appendAll(Map((key, AmqpFieldEncoder[T].encode(value))))

  def ++(that: Headers): Headers =
    appendAll(that.toMap)

  def appendAll(that: Map[HeaderKey, AmqpFieldValue]): Headers =
    Headers(toMap ++ that)

  // prepend
  def +:[T: AmqpFieldEncoder](pair: (HeaderKey, T)): Headers =
    prepend(pair._1, pair._2)

  def prepend[T: AmqpFieldEncoder](key: HeaderKey, value: T): Headers =
    prependAll(Map((key, AmqpFieldEncoder[T].encode(value))))

  def prependAll(that: Map[HeaderKey, AmqpFieldValue]): Headers =
    Headers(that ++ toMap)

  // remove
  def remove(key: HeaderKey, keys: HeaderKey*): Headers =
    Headers(toMap -- (key +: keys))

  // as
  /** Decodes the value of the mandatory header to the specified type `T`.
    *   - If the header is missing, it will raise a `MissingHeader` error.
    *   - If the decoding fails, it will raise a `DecodingError` error.
    * @param name
    *   the name of the header
    */
  def getAs[F[_], T: AmqpFieldDecoder](name: HeaderKey)(implicit F: ApplicativeThrow[F]): F[T] =
    F.fromEither(getRaw[Either[Throwable, *]](name).flatMap(_.as[T]))

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
    F.fromEither(getOptRaw(name).map(_.as[T]).sequence)

  // raw
  /** Returns the raw value of the mandatory header.
    *   - If the header is missing, it will raise a `MissingHeader` error.
    * @param name
    *   the name of the header
    */
  def getRaw[F[_]](name: HeaderKey)(implicit F: ApplicativeThrow[F]): F[AmqpFieldValue] =
    getOptRaw(name) match {
      case Some(value) => F.pure(value)
      case None        => F.raiseError(new MissingHeader(name))
    }

  /** Returns the raw optional value of the header.
    *   - If the header is missing, it will return `None`.
    * @param name
    *   the name of the header
    */
  def getOptRaw(name: HeaderKey): Option[AmqpFieldValue] =
    toMap.get(name)

  // backwards compatibility - to remove in the future versions
  // TODO Choose the version
  @deprecated("Use getAs[F, AmqpFieldValue] instead", "2.0.0")
  def get(name: HeaderKey): Option[AmqpFieldValue] =
    getOptRaw(name)

  // TODO Choose the version
  @deprecated("Use getAs[F, AmqpFieldValue] instead", "2.0.0")
  def apply[F[_]: ApplicativeThrow](name: HeaderKey): AmqpFieldValue =
    getOptRaw(name).getOrElse(throw new MissingHeader(name))
}
object Headers extends HeadersSyntax {

  val empty: Headers                                         = new Headers(Map.empty)
  def apply(values: Header*): Headers                        = Headers(values.toMap)
  def apply(values: Map[HeaderKey, AmqpFieldValue]): Headers = new Headers(values)

  class MissingHeader(name: String) extends RuntimeException(s"Missing header: $name")

  private[fs2rabbit] def unsafeFromMap(values: Map[String, AnyRef]): Headers =
    Headers(values.map { case (k, v) => k -> AmqpFieldValue.unsafeFrom(v) })

  // instances
  implicit val headersEq: Eq[Headers] =
    Eq[Map[HeaderKey, AmqpFieldValue]]
      .contramap(_.toMap)
}

private[fs2rabbit] sealed trait HeadersSyntax {
  implicit class HeaderKeyOps(private val key: HeaderKey) {
    def :=[T: AmqpFieldEncoder](value: T): (HeaderKey, AmqpFieldValue) = (key, AmqpFieldEncoder[T].encode(value))
  }
}
