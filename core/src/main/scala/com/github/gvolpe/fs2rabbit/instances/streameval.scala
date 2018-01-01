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

package com.github.gvolpe.fs2rabbit.instances

import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.typeclasses.StreamEval
import fs2.{Pipe, Sink, Stream}

object streameval {

  implicit def syncStreamEvalInstance[F[_]](implicit F: Sync[F]): StreamEval[F] =
    new StreamEval[F] {
      override def evalF[A](body: => A): Stream[F, A] =
        Stream.eval[F, A](F.delay(body))

      override def liftSink[A](f: A => F[Unit]): Sink[F, A] =
        liftPipe[A, Unit](f)

      override def liftPipe[A, B](f: A => F[B]): Pipe[F, A, B] =
        _.evalMap(f)
    }

}
