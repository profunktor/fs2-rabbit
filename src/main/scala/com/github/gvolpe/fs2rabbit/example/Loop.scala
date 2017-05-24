package com.github.gvolpe.fs2rabbit.example

import fs2.util.NonFatal
import fs2.{Scheduler, Strategy, Stream, Task}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object Loop {

  private val log = LoggerFactory.getLogger(getClass)

  def run(program: () => Stream[Task, Unit])(implicit S: Strategy, R: Scheduler): Unit =
    loop(program).unsafeRunAsync(_ => ())

  private def loop(program: () => Stream[Task, Unit])(implicit S: Strategy, R: Scheduler): Task[Unit] =
    program().run.handleWith(failureHandler(loop(program)))

  private def failureHandler(program: => Task[Unit])(implicit S: Strategy, R: Scheduler): PartialFunction[Throwable, Task[Unit]] = {
    case NonFatal(reason) =>
      log.error(s"${reason.getMessage}, restarting in 5 seconds...")
      program.schedule(5.seconds)
    case error =>
      log.error(s"Streaming has finished unexpectedly: ${error.getMessage}, restarting in 5 seconds...")
      program.schedule(5.seconds)
  }

}
