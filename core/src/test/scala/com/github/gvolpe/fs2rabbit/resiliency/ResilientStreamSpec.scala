/*
 * Copyright 2017-2019 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.resiliency

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.apply._
import com.github.gvolpe.fs2rabbit.effects.Log
import fs2._
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ResilientStreamSpec extends FlatSpecLike with Matchers {

  implicit val timer = IO.timer(ExecutionContext.global)

  private val sink: Pipe[IO, Int, Unit] = _.evalMap(n => IO(println(n)))

  object IOAssertion {
    def apply[A](ioa: IO[A]): A = ioa.unsafeRunSync()
  }

  implicit val logger: Log[IO] = new Log[IO] {
    override def info(value: String): IO[Unit]     = IO(println(value))
    override def error(error: Throwable): IO[Unit] = IO(println(error.getMessage))
  }

  it should "run a stream until it's finished" in IOAssertion {
    val program = Stream(1, 2, 3).covary[IO].through(sink)
    ResilientStream.run(program)
  }

  it should "run a stream and recover in case of failure" in IOAssertion {
    val errorProgram = Stream.raiseError[IO](new Exception("on purpose")).through(sink)

    def p(ref: Ref[IO, Int]): Stream[IO, Unit] =
      errorProgram.handleErrorWith { t =>
        Stream.eval(ref.get) flatMap { n =>
          if (n == 0) Stream.eval(IO.unit)
          else Stream.eval(ref.update(_ - 1) *> IO.raiseError(t))
        }
      }

    Ref.of[IO, Int](2).flatMap(ref => ResilientStream.run(p(ref), 1.second))
  }

}
