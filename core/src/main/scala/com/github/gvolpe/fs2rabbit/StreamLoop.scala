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

import cats.effect.{Effect, IO}
import fs2.{Scheduler, Stream}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * It provides a resilient run method for an effectful [[fs2.Stream]] that will run forever with
  * automatic error recovery.
  *
  * In case of failure, the entire stream will be restarted after the specified retry time.
  *
  * @see the StreamLoopSpec that demonstrates an use case.
  * */
object StreamLoop {

  private val log = LoggerFactory.getLogger(getClass)

  def run[F[_]](program: () => Stream[F, Unit],
                retry: FiniteDuration = 5.seconds)
               (implicit F: Effect[F], ec: ExecutionContext): IO[Unit] =
    F.runAsync(loop(program(), retry).run) {
      case Right(_) => IO.unit
      case Left(e)  => IO.raiseError(e)
    }

  private def loop[F[_] : Effect](program: Stream[F, Unit], retry: FiniteDuration)
                                 (implicit ec: ExecutionContext): Stream[F, Unit] = {
    program.handleErrorWith { err =>
      log.error(s"$err")
      log.info(s"Restarting in $retry...")
      val scheduledProgram = Scheduler[F](2).flatMap(_.sleep[F](retry)).flatMap(_ => program)
      loop[F](scheduledProgram, retry)
    }
  }

}