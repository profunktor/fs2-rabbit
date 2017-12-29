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
import Fs2Utils.asyncF
import fs2._
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class StreamLoopSpec extends FlatSpecLike with Matchers {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  implicit val es = new EffectScheduler[IO] {
    override def schedule[A](effect: IO[A], delay: FiniteDuration)(implicit ec: ExecutionContext) = {
      Scheduler[IO](2).flatMap(_.sleep[IO](delay)).run.flatMap(_ => effect)
    }
  }

  implicit val runner = new EffectUnsafeSyncRunner[IO] {
    override def unsafeRunSync(effect: IO[Unit]) = effect.unsafeRunSync()
  }

  it should "run a stream until it's finished" in {
    val sink = Fs2Utils.liftSink[IO, Int](n => IO(println(n)))
    val program = Stream(1,2,3).covary[IO] to sink
    StreamLoop.run(() => program)
  }

  it should "run a stream and recover in case of failure" in {
    val sink: Sink[IO, Int] = streamN => {
      streamN.map { n => println(n) }
    }

    val program = Stream.raiseError(new Exception("on purpose")).covary[IO] to sink

    var trigger: Int = 2

    val p: Stream[IO, Unit] = program.handleErrorWith { t =>
      if (trigger == 0) asyncF[IO, Unit](())
      else {
        trigger = trigger - 1
        Stream.raiseError(t)
      }
    }

    StreamLoop.run(() => p, 1.second)
  }

}
