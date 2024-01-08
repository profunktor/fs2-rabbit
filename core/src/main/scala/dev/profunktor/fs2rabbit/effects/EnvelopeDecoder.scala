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

package dev.profunktor.fs2rabbit.effects
import cats.{Applicative, ApplicativeThrow}
import cats.data.Kleisli
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, AmqpProperties, ExchangeName, RoutingKey}
import dev.profunktor.fs2rabbit.model.AmqpFieldValue._
import cats.implicits._

object EnvelopeDecoder {
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

  def header[F[_]](name: String)(implicit F: ApplicativeThrow[F]): EnvelopeDecoder[F, AmqpFieldValue] =
    Kleisli(e => F.catchNonFatal(e.properties.headers(name)))

  def optHeader[F[_]: Applicative](name: String): EnvelopeDecoder[F, Option[AmqpFieldValue]] =
    Kleisli(_.properties.headers.get(name).pure[F])

  def stringHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, String] =
    headerPF[F, String](name) { case StringVal(a) => a }

  def intHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Int] =
    headerPF[F, Int](name) { case IntVal(a) => a }

  def longHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Long] =
    headerPF[F, Long](name) { case LongVal(a) => a }

  def arrayHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, collection.Seq[Any]] =
    headerPF[F, collection.Seq[Any]](name) { case ArrayVal(a) => a }

  def optStringHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[String]] =
    optHeaderPF[F, String](name) { case StringVal(a) => a }

  def optIntHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[Int]] =
    optHeaderPF[F, Int](name) { case IntVal(a) => a }

  def optLongHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[Long]] =
    optHeaderPF[F, Long](name) { case LongVal(a) => a }

  def optArrayHeader[F[_]: ApplicativeThrow](name: String): EnvelopeDecoder[F, Option[collection.Seq[Any]]] =
    optHeaderPF[F, collection.Seq[Any]](name) { case ArrayVal(a) => a }

  private def headerPF[F[_], A](
      name: String
  )(pf: PartialFunction[AmqpFieldValue, A])(implicit F: ApplicativeThrow[F]): EnvelopeDecoder[F, A] =
    Kleisli { env =>
      F.catchNonFatal(pf(env.properties.headers(name)))
    }

  private def optHeaderPF[F[_], A](name: String)(pf: PartialFunction[AmqpFieldValue, A])(implicit
      F: ApplicativeThrow[F]
  ): EnvelopeDecoder[F, Option[A]] =
    Kleisli(_.properties.headers.get(name).traverse(h => F.catchNonFatal(pf(h))))
}
