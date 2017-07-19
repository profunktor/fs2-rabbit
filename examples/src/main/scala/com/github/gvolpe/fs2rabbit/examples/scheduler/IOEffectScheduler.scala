package com.github.gvolpe.fs2rabbit.examples.scheduler

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.EffectScheduler
import fs2.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object IOEffectScheduler extends EffectScheduler[IO] {

  override def schedule[A](effect: IO[A], delay: FiniteDuration)(implicit ec: ExecutionContext) = {
    Scheduler[IO](2).flatMap(_.sleep[IO](delay)).run.flatMap(_ => effect)
  }

}
