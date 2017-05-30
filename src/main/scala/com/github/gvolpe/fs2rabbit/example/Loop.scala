package com.github.gvolpe.fs2rabbit.example

import fs2.{Scheduler, Strategy, Stream, Task}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object Loop {

  private val log = LoggerFactory.getLogger(getClass)

  def run(program: () => Stream[Task, Unit])(implicit S: Strategy, R: Scheduler): Unit =
    loop(program().run)

  def loop(program: Task[Unit])(implicit S: Strategy, R: Scheduler): Unit =
    program.unsafeAttemptRun() match {
      case Left(err) =>
        log.error(s"$err, restarting in 5 seconds...")
        loop(program.schedule(5.seconds))
      case Right(()) =>
        Task.delay(())
    }

}
