package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import fs2.{Scheduler, Stream}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object StreamLoop {

  private val log = LoggerFactory.getLogger(getClass)

  def run(program: () => Stream[IO, Unit], retry: FiniteDuration = 5.seconds)
         (implicit ec: ExecutionContext, s: Scheduler): Unit =
    loop(program().run, retry)

  private def loop(program: IO[Unit], retry: FiniteDuration)
                  (implicit ec: ExecutionContext, s: Scheduler): Unit =
    program.attempt.unsafeRunSync() match {
      case Left(err) =>
        log.error(s"$err, restarting in $retry...")
        loop(program.schedule(retry), retry)
      case Right(()) =>
        IO(())
    }

  implicit class IOOps[A](ioa: IO[A]) {
    def schedule(delay: FiniteDuration)
                (implicit ec: ExecutionContext, s: Scheduler): IO[A] =
      IO.async[Unit] { cb => s.scheduleOnce(delay)(cb(Right(()))) }.flatMap(_ => ioa)
  }

}
