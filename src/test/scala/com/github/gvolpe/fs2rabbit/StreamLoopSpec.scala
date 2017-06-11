package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import fs2._
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.util.Random

class StreamLoopSpec extends FlatSpecLike with Matchers {

  implicit val appS = scala.concurrent.ExecutionContext.Implicits.global
  implicit val appR = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")

  it should "run a stream until it's finished" in {
    val sink = Fs2Utils.liftSink[Int](n => IO(println(n)))
    val program = Stream(1,2,3).covary[IO] to sink
    StreamLoop.run(() => program)
  }

  ignore should "run a stream and recover in case of failure" in {
    val sink: Sink[IO, Int] = streamN => {
      streamN.flatMap { n =>
        if (Random.nextInt(5) == n) Stream.fail(new Exception("on purpose"))
        else Stream.eval(IO(println(n)))
      }
    }
    val program = Stream(1,2,3).covary[IO] to sink
    StreamLoop.run(() => program, 1.second)
  }

}
