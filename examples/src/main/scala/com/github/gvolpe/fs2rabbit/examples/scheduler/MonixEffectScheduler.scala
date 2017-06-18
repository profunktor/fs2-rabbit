package com.github.gvolpe.fs2rabbit.examples.scheduler

import com.github.gvolpe.fs2rabbit.EffectScheduler
import fs2.Scheduler
import monix.eval.Task

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object MonixEffectScheduler extends EffectScheduler[Task] {

  override def schedule[A](effect: Task[A], delay: FiniteDuration)
                          (implicit ec: ExecutionContext, s: Scheduler) = {
    effect.delayExecution(delay)
  }

}
