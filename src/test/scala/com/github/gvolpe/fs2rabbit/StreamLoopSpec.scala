package com.github.gvolpe.fs2rabbit

import fs2._
import org.scalatest.{FlatSpecLike, Matchers}
import scala.concurrent.duration._

import scala.util.Random

class StreamLoopSpec extends FlatSpecLike with Matchers {

  implicit val appS: Strategy   = fs2.Strategy.fromFixedDaemonPool(2, "fs2-rabbit-demo")
  implicit val appR: Scheduler  = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")

  it should "run a stream until it's finished" in {
    val sink = Fs2Utils.liftSink[Int](n => Task.delay(println(n)))
    val program = Stream(1,2,3) to sink
    StreamLoop.run(() => program)
  }

  it should "run a stream and recover in case of failure" in {
    val sink: Sink[Task, Int] = streamN => {
      streamN.flatMap { n =>
        if (Random.nextInt(5) == n) Stream.fail(new Exception("on purpose"))
        else Stream.eval(Task.delay(println(n)))
      }
    }
    val program = Stream(1,2,3) to sink
    StreamLoop.run(() => program, 1.second)
  }

}
