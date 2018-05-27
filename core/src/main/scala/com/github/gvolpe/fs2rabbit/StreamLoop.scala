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

package com.github.gvolpe.fs2rabbit

import cats.effect.{Effect, Timer}
import com.github.gvolpe.fs2rabbit.typeclasses.Log
import fs2.Stream

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * It provides a resilient run method for an effectful [[fs2.Stream]] that will run forever with
  * automatic error recovery.
  *
  * In case of failure, the entire stream will be restarted after the specified retry time with an
  * exponential backoff.
  *
  * By default the program will be restarted in 5 seconds, then 10, then 15, etc.
  *
  * @see the StreamLoopSpec that demonstrates a use case.
  * */
object StreamLoop {

  def run[F[_]](
      program: Stream[F, Unit],
      retry: FiniteDuration = 5.seconds)(implicit F: Effect[F], T: Timer[F], ec: ExecutionContext, L: Log[F]): F[Unit] =
    loop(program, retry, 1).compile.drain

  private def loop[F[_]: Effect](program: Stream[F, Unit],
                                 retry: FiniteDuration,
                                 count: Int)(implicit ec: ExecutionContext, T: Timer[F], L: Log[F]): Stream[F, Unit] =
    program.handleErrorWith {
      case NonFatal(err) =>
        val scheduledProgram = Stream.eval(T.sleep(retry)).flatMap(_ => program)
        for {
          _ <- Stream.eval(L.error(err))
          _ <- Stream.eval(L.info(s"Restarting in ${retry * count}..."))
          p <- loop[F](scheduledProgram, retry, count + 1)
        } yield p
    }

}
