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

import cats.effect.IO
import cats.syntax.apply._
import fs2._
import fs2.async.Ref
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class StreamLoopSpec extends FlatSpecLike with Matchers {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val sink: Sink[IO, Int] = _.evalMap(n => IO(println(n)))

  object IOAssertion {
    def apply[A](ioa: IO[A]): Unit = ioa.unsafeRunSync()
  }

  it should "run a stream until it's finished" in IOAssertion {
    val program = Stream(1, 2, 3).covary[IO] to sink
    StreamLoop.run(() => program)
  }

  it should "run a stream and recover in case of failure" in IOAssertion {
    val errorProgram = Stream.raiseError(new Exception("on purpose")).covary[IO] to sink

    def errorHandler(ref: Ref[IO, Int], t: Throwable): Stream[IO, Unit] =
      Stream.eval(ref.get) flatMap { n =>
        if (n == 0) Stream.eval(IO.unit)
        else Stream.eval(ref.modify(_ - 1) *> IO.raiseError(t))
      }

    val p: Stream[IO, Unit] =
      for {
        ref <- Stream.eval(async.refOf[IO, Int](2))
        _   <- errorProgram.handleErrorWith(errorHandler(ref, _))
      } yield ()

    StreamLoop.run(() => p, 1.second)
  }

}
