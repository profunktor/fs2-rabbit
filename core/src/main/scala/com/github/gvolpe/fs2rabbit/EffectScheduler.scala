package com.github.gvolpe.fs2rabbit

import cats.effect.Effect
import fs2.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

trait EffectScheduler[F[_]] {
  def schedule[A](body: F[A], delay: FiniteDuration)(implicit ec: ExecutionContext, s: Scheduler, F: Effect[F]): F[A]
  def unsafeRunSync(effect: F[Unit]): Unit
}
