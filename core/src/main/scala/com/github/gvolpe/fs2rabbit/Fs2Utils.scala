package com.github.gvolpe.fs2rabbit

import cats.effect.Sync
import fs2.{Pipe, Sink, Stream}

import scala.language.higherKinds

object Fs2Utils {

  def asyncF[F[_] : Sync, A](body: => A): Stream[F, A] =
    Stream.eval[F, A] { Sync[F].delay(body) }

  def liftSink[F[_], A](f: A => F[Unit]): Sink[F, A] =
    liftPipe[F, A, Unit](f)

  def liftPipe[F[_], A, B](f: A => F[B]): Pipe[F, A, B] =
    _.evalMap (f)

}
