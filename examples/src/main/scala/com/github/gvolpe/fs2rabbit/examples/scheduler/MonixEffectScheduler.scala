package com.github.gvolpe.fs2rabbit.examples.scheduler

import com.github.gvolpe.fs2rabbit.EffectScheduler
import monix.eval.Task

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object MonixEffectScheduler extends EffectScheduler[Task] {

  override def schedule[A](effect: Task[A], delay: FiniteDuration)
                          (implicit ec: ExecutionContext): Task[A] = {
    effect.delayExecution(delay)
  }

}
