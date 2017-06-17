package com.github.gvolpe.fs2rabbit.examples.scheduler

import com.github.gvolpe.fs2rabbit.EffectScheduler
import fs2.Scheduler
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MonixEffectScheduler extends EffectScheduler[Task] {

  override def schedule[A](effect: Task[A], delay: FiniteDuration)
                          (implicit ec: ExecutionContext, s: Scheduler) = {
    effect.delayExecution(delay)
  }

  override def unsafeRunSync(effect: Task[Unit]) = {
    effect.runSyncMaybe
  }
}
