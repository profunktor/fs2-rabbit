package com.github.gvolpe.fs2rabbit.examples.runner

import com.github.gvolpe.fs2rabbit.EffectUnsafeSyncRunner
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object MonixEffectRunner extends EffectUnsafeSyncRunner[Task] {

  override def unsafeRunSync(effect: Task[Unit]): Unit = {
    Await.result(effect.runAsync, Duration.Inf)
  }

}
