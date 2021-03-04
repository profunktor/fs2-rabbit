package dev.profunktor.fs2rabbit

import cats.data.EitherT
import cats.effect.kernel.MonadCancel
import cats.~>
import cats.implicits._
import cats.tagless.implicits.toFunctorKOps
import dev.profunktor.fs2rabbit.algebra.ConnectionResource

package object interpreter {
  class RabbitClientOps[F[_]](val client: RabbitClient[F]) extends AnyVal {
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

    /** *
      * Transforms the rabbit client into one where all errors from the effect are caught and lifted into EitherT's error channel
      */
    def liftAttemptK(implicit F: MonadCancel[F, Throwable]): RabbitClient[EitherT[F, Throwable, *]] =
      imapK[EitherT[F, Throwable, *]](EitherT.liftAttemptK)(
        new (EitherT[F, Throwable, *] ~> F) {
          def apply[A](fa: EitherT[F, Throwable, A]): F[A] = fa.value.flatMap {
            case Right(a) => F.pure(a)
            case Left(e)  => F.raiseError(e)
          }
        }
      )
  }
}
