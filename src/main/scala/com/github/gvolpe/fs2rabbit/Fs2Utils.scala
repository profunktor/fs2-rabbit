package com.github.gvolpe.fs2rabbit

import fs2.{Pipe, Sink, Stream, Task}

object Fs2Utils {

  def async[A](body: => A): Stream[Task, A] = Stream.eval(Task.delay(body))

  def liftSink[A](f: A => Task[Unit]): Sink[Task, A]     = liftPipe[A, Unit](f)

  def liftPipe[A, B](f: A => Task[B]): Pipe[Task, A, B]  = _.evalMap (f)

}
