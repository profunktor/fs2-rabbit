package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import fs2.{Pipe, Scheduler, Sink, Stream}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Fs2Utils {

  def async[A](body: => A): Stream[IO, A] = Stream.eval(IO(body))

  def liftSink[A](f: A => IO[Unit]): Sink[IO, A]    = liftPipe[A, Unit](f)

  def liftPipe[A, B](f: A => IO[B]): Pipe[IO, A, B] = _.evalMap (f)

  implicit class IOOps[A](ioa: IO[A]) {
    def schedule(delay: FiniteDuration)
                (implicit ec: ExecutionContext, s: Scheduler): IO[A] =
      IO.async[Unit] { cb => s.scheduleOnce(delay)(cb(Right(()))) }.flatMap(_ => ioa)
  }

}
