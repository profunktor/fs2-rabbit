package com.github.gvolpe.fs2rabbit.examples.runner

import com.github.gvolpe.fs2rabbit.EffectUnsafeSyncRunner

import scalaz.concurrent.Task

object ScalazEffectRunner extends EffectUnsafeSyncRunner[Task] {

  override def unsafeRunSync(effect: Task[Unit]) = effect.unsafePerformSync

}
