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

import cats.effect.Sync
import cats.syntax.functor._
import fs2.{Pipe, Stream}

trait StreamEval[F[_]] {
  def pure[A](body: A): Stream[F, A]
  def evalF[A](body: => A): Stream[F, A]
  def evalDiscard[A](body: => A): Stream[F, Unit]
  def liftSink[A](f: A => F[Unit]): Pipe[F, A, Unit]
  def liftPipe[A, B](f: A => F[B]): Pipe[F, A, B]
}

object StreamEval {

  implicit def syncStreamEvalInstance[F[_]: Sync]: StreamEval[F] =
    new StreamEval[F] {
      override def pure[A](body: A): Stream[F, A] =
        Stream(body).covary[F]

      override def evalF[A](body: => A): Stream[F, A] =
        Stream.eval(Sync[F].delay(body))

      override def evalDiscard[A](body: => A): Stream[F, Unit] =
        Stream.eval(Sync[F].delay(body).void)

      override def liftSink[A](f: A => F[Unit]): Pipe[F, A, Unit] =
        liftPipe[A, Unit](f)

      override def liftPipe[A, B](f: A => F[B]): Pipe[F, A, B] =
        _.evalMap(f)
    }

}
