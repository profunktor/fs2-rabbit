package com.github.gvolpe.fs2rabbit.examples.scheduler

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.EffectScheduler
import fs2.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object IOEffectScheduler extends EffectScheduler[IO] {

  override def schedule[A](effect: IO[A], delay: FiniteDuration)
                          (implicit ec: ExecutionContext, s: Scheduler) = {
    IO.async[Unit] { cb => s.scheduleOnce(delay)(cb(Right(()))) }.flatMap(_ => effect)
  }

  override def unsafeRunSync(effect: IO[Unit]) = effect.unsafeRunSync()

}
