package com.github.gvolpe.fs2rabbit.examples.scheduler

import com.github.gvolpe.fs2rabbit.EffectScheduler
import fs2.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Task

object ScalazEffectScheduler extends EffectScheduler[Task] {

  override def schedule[A](effect: Task[A], delay: FiniteDuration)
                          (implicit ec: ExecutionContext, s: Scheduler): Task[A] =
    Task.schedule(effect.unsafePerformSync, delay)

}
