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
import cats._
import cats.data._
import cats.implicits._
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8

case class AmqpEnvelope[A](
    deliveryTag: DeliveryTag,
    payload: A,
    properties: AmqpProperties,
    exchangeName: ExchangeName,
    routingKey: RoutingKey,
    redelivered: Boolean
)
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
