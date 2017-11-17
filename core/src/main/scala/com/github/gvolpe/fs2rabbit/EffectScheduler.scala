package com.github.gvolpe.fs2rabbit

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
  * A generic scheduler representation for any [[cats.effect.Effect]] that is able to schedule
  * effects to run later on.
  *
  * Some effects like the Monix Task and the Scalaz Task have support for scheduling effects.
  * With this abstraction, we can do it generically for any given effect.
  * */
trait EffectScheduler[F[_]] {
  /**
    * It creates an Effect that will be submitted for execution after the given delay.
    * */
  def schedule[A](effect: F[A], delay: FiniteDuration)(implicit ec: ExecutionContext): F[A]
}

object EffectScheduler {
  def apply[F[_] : EffectScheduler]: EffectScheduler[F] = implicitly[EffectScheduler[F]]
}