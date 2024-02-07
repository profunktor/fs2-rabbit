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

package dev.profunktor.fs2rabbit.interpreter

import cats.data.EitherT
import cats.effect.MonadCancelThrow
import cats.effect.kernel.MonadCancel
import cats.~>
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.ConnectionResource

final class RabbitClientOps[F[_]](val client: RabbitClient[F]) extends AnyVal {
  import ConnectionResource._
  def imapK[G[_]](fk: F ~> G)(gk: G ~> F)(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): RabbitClient[G] =
    new RabbitClient[G](
      client.connection.mapK(fk),
      client.binding.mapK(fk),
      client.declaration.mapK(fk),
      client.deletion.mapK(fk),
      client.consumingProgram.imapK(fk)(gk),
      client.publishingProgram.imapK(fk)(gk)
    )

  /** * Transforms the rabbit client into one where all errors from the effect are caught and lifted into EitherT's
    * error channel
    */
  def liftAttemptK(implicit F: MonadCancelThrow[F]): RabbitClient[EitherT[F, Throwable, *]] =
    imapK[EitherT[F, Throwable, *]](EitherT.liftAttemptK)(
      new (EitherT[F, Throwable, *] ~> F) {
        def apply[A](fa: EitherT[F, Throwable, A]): F[A] = fa.value.flatMap {
          case Right(a) => F.pure(a)
          case Left(e)  => F.raiseError(e)
        }
      }
    )
}
