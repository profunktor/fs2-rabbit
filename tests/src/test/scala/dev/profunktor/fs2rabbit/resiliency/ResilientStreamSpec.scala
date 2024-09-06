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

import cats.effect.IO
import cats.effect.kernel.Ref
import dev.profunktor.fs2rabbit.BaseSpec
import fs2._
import cats.effect.unsafe.implicits.global

import scala.concurrent.duration._
import org.scalatest.compatible.Assertion

class ResilientStreamSpec extends BaseSpec {

  private val sink: Pipe[IO, Int, Unit] = _.evalMap(putStrLn)

  val emptyAssertion: Assertion = true shouldBe true

  it should "run a stream until it's finished" in {
    val program = Stream(1, 2, 3).covary[IO].through(sink)
    ResilientStream.run(program).as(emptyAssertion).unsafeToFuture()
  }

  it should "run a stream and recover in case of failure" in {
    val errorProgram = Stream.raiseError[IO](new Exception("on purpose")).through(sink)

    def p(ref: Ref[IO, Int]): Stream[IO, Unit] =
      errorProgram.handleErrorWith { t =>
        Stream.eval(ref.get) flatMap { n =>
          if (n == 0) Stream.eval(IO.unit)
          else Stream.eval(ref.update(_ - 1) *> IO.raiseError(t))
        }
      }

    Ref.of[IO, Int](2).flatMap(r => ResilientStream.run(p(r), 1.second)).as(emptyAssertion).unsafeToFuture()
  }

}
