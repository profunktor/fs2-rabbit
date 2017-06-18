package com.github.gvolpe.fs2rabbit

import com.github.gvolpe.fs2rabbit.examples.runner.{IOEffectRunner, MonixEffectRunner}
import com.github.gvolpe.fs2rabbit.examples.scheduler.{IOEffectScheduler, MonixEffectScheduler}

package object examples {

  implicit val iOEffectScheduler    = IOEffectScheduler
  implicit val ioEffectRunner       = IOEffectRunner
  implicit val monixEffectScheduler = MonixEffectScheduler
  implicit val monixEffectRunner    = MonixEffectRunner

}
