package com.github.gvolpe.fs2rabbit.examples.runner

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.EffectUnsafeSyncRunner

object IOEffectRunner extends EffectUnsafeSyncRunner[IO] {
  override def unsafeRunSync(effect: IO[Unit]) = effect.unsafeRunSync()
}