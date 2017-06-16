package com.github.gvolpe.fs2rabbit

import cats.effect.{Effect, IO}
import fs2.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object IOEffectScheduler extends EffectScheduler[IO] {

  override def schedule[A](body: IO[A], delay: FiniteDuration)
                          (implicit ec: ExecutionContext, s: Scheduler, F: Effect[IO]) = {
    IO.async[Unit] { cb => s.scheduleOnce(delay)(cb(Right(()))) }.flatMap(_ => body)
  }

  override def unsafeRunSync(effect: IO[Unit]) = effect.unsafeRunSync()

}
