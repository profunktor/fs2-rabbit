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

package dev.profunktor.fs2rabbit.resiliency

import cats.effect.Temporal
import cats.syntax.apply._
import dev.profunktor.fs2rabbit.effects.Log
import fs2.Stream

import scala.concurrent.duration._
import scala.util.control.NonFatal

/** It provides a resilient run method for an effectful `fs2.Stream` that will run forever with automatic error
  * recovery.
  *
  * In case of failure, the entire stream will be restarted after the specified retry time with an exponential backoff.
  *
  * By default the program will be restarted in 5 seconds, then 10, then 15, etc.
  *
  * @see
  *   ResilientStreamSpec for more.
  */
object ResilientStream {

  def runF[F[_]: Log: Temporal](program: F[Unit], retry: FiniteDuration = 5.seconds): F[Unit] =
    run(Stream.eval(program), retry)

  def run[F[_]: Log: Temporal](
      program: Stream[F, Unit],
      retry: FiniteDuration = 5.seconds
  ): F[Unit] =
    loop(program, retry, 1).compile.drain

  private def loop[F[_]: Log: Temporal](
      program: Stream[F, Unit],
      retry: FiniteDuration,
      count: Int
  ): Stream[F, Unit] =
    program.handleErrorWith {
      case NonFatal(err) =>
        Stream.eval(Log[F].error(err.getMessage) *> Log[F].info(s"Restarting in ${retry.toSeconds * count}...")) >>
          loop[F](Stream.sleep(retry * count.toLong) >> program, retry, count + 1)
      case _             => ???
    }

}
