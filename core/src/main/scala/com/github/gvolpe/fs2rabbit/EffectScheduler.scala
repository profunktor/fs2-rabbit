package com.github.gvolpe.fs2rabbit

import fs2.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

trait EffectScheduler[F[_]] {
  def schedule[A](effect: F[A], delay: FiniteDuration)(implicit ec: ExecutionContext, s: Scheduler): F[A]
  def unsafeRunSync(effect: F[Unit]): Unit
}
