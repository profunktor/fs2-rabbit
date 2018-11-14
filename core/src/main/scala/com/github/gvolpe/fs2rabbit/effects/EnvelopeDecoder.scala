/*
 * Copyright 2017 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.effects
import cats.{Applicative, ApplicativeError}
import cats.data.Kleisli
import com.github.gvolpe.fs2rabbit.model.{AmqpHeaderVal, AmqpProperties}
import com.github.gvolpe.fs2rabbit.model.AmqpHeaderVal._
import cats.implicits._

object EnvelopeDecoder {
  def apply[F[_], A](implicit e: EnvelopeDecoder[F, A]): EnvelopeDecoder[F, A] = e

  def properties[F[_]: Applicative]: EnvelopeDecoder[F, AmqpProperties] =
    Kleisli(e => e.properties.pure[F])

  def payload[F[_]: Applicative]: EnvelopeDecoder[F, Array[Byte]] =
    Kleisli(_.payload.pure[F])

  def header[F[_]](name: String)(implicit F: ApplicativeError[F, Throwable]): EnvelopeDecoder[F, AmqpHeaderVal] =
    Kleisli(e => F.catchNonFatal(e.properties.headers(name)))

  def optHeader[F[_]: Applicative](name: String): EnvelopeDecoder[F, Option[AmqpHeaderVal]] =
    Kleisli(_.properties.headers.get(name).pure[F])

  def stringHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, String] =
    headerPF[F, String](name) { case StringVal(a) => a }

  def intHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, Int] =
    headerPF[F, Int](name) { case IntVal(a) => a }

  def longHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, Long] =
    headerPF[F, Long](name) { case LongVal(a) => a }

  def arrayHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, Seq[Any]] =
    headerPF[F, Seq[Any]](name) { case ArrayVal(a) => a }

  def optStringHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, Option[String]] =
    optHeaderPF[F, String](name) { case StringVal(a) => a }

  def optIntHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, Option[Int]] =
    optHeaderPF[F, Int](name) { case IntVal(a) => a }

  def optLongHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, Option[Long]] =
    optHeaderPF[F, Long](name) { case LongVal(a) => a }

  def optArrayHeader[F[_]: ApplicativeError[?[_], Throwable]](name: String): EnvelopeDecoder[F, Option[Seq[Any]]] =
    optHeaderPF[F, Seq[Any]](name) { case ArrayVal(a) => a }

  private def headerPF[F[_], A](name: String)(pf: PartialFunction[AmqpHeaderVal, A])(
      implicit F: ApplicativeError[F, Throwable]): EnvelopeDecoder[F, A] =
    Kleisli { env =>
      F.catchNonFatal(pf(env.properties.headers(name)))
    }

  private def optHeaderPF[F[_], A](name: String)(pf: PartialFunction[AmqpHeaderVal, A])(
      implicit F: ApplicativeError[F, Throwable]): EnvelopeDecoder[F, Option[A]] =
    Kleisli(_.properties.headers.get(name).traverse(h => F.catchNonFatal(pf(h))))
}
