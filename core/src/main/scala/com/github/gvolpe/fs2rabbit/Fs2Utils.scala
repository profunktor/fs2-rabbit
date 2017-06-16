package com.github.gvolpe.fs2rabbit

import cats.effect.Effect
import fs2.{Pipe, Sink, Stream}

import scala.language.higherKinds

object Fs2Utils {

  def asyncF[F[_], A](body: => A)(implicit F: Effect[F]): Stream[F, A] =
    Stream.eval[F, A] { F.delay(body) }

  def liftSink[F[_], A](f: A => F[Unit])(implicit F: Effect[F]): Sink[F, A] =
    liftPipe[F, A, Unit](f)

  def liftPipe[F[_], A, B](f: A => F[B]): Pipe[F, A, B] =
    _.evalMap (f)

}
