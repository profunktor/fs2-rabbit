package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import Fs2Utils.asyncF
import fs2._
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class StreamLoopSpec extends FlatSpecLike with Matchers {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  implicit val s  = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")

  implicit val es = new EffectScheduler[IO] {
    override def schedule[A](effect: IO[A], delay: FiniteDuration)
                            (implicit ec: ExecutionContext, s: Scheduler) = {
      IO.async[Unit] { cb => s.scheduleOnce(delay)(cb(Right(()))) }.flatMap(_ => effect)
    }
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

    val program = Stream.fail(new Exception("on purpose")).covary[IO] to sink

    var trigger: Int = 2

    val p: Stream[IO, Unit] = program.onError { t =>
      if (trigger == 0) asyncF[IO, Unit]()
      else {
        trigger = trigger - 1
        Stream.fail(t)
      }
    }

    StreamLoop.run(() => p, 1.second)
  }

}
