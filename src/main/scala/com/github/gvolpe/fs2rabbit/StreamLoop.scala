package com.github.gvolpe.fs2rabbit

import fs2.{Scheduler, Strategy, Stream, Task}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object StreamLoop {

  private val log = LoggerFactory.getLogger(getClass)

  def run(program: () => Stream[Task, Unit])(retry: FiniteDuration = 5.seconds)(implicit S: Strategy, R: Scheduler): Unit =
    loop(program().run)(retry)

  def loop(program: Task[Unit])(retry: FiniteDuration)(implicit S: Strategy, R: Scheduler): Unit =
    program.unsafeAttemptRun() match {
      case Left(err) =>
        log.error(s"$err, restarting in $retry...")
        loop(program.schedule(retry))
      case Right(()) =>
        Task.delay(())
    }

}
