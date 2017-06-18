package com.github.gvolpe.fs2rabbit

import fs2.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

/**
  * A generic scheduler representation for any [[cats.effect.Effect]] that is able to schedule
  * effects to run later on.
  *
  * Some effects like the Monix Task and the Scalaz Task have support for scheduling effects.
  * With this abstraction, we can do it generically for any given effect.
  * */
trait EffectScheduler[F[_]] {
  def schedule[A](effect: F[A], delay: FiniteDuration)(implicit ec: ExecutionContext, s: Scheduler): F[A]
}
