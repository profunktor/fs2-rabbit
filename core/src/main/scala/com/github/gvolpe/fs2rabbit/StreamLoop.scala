package com.github.gvolpe.fs2rabbit

import cats.effect.Effect
import fs2.{Scheduler, Stream}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.higherKinds

object StreamLoop {

  private val log = LoggerFactory.getLogger(getClass)

  def run[F[_]](program: () => Stream[F, Unit], retry: FiniteDuration = 5.seconds)
         (implicit ec: ExecutionContext, s: Scheduler, F: Effect[F], ES: EffectScheduler[F], R: EffectUnsafeSyncRunner[F]): Unit =
    R.unsafeRunSync(loop(program(), retry).run)

  private def loop[F[_]](program: Stream[F, Unit], retry: FiniteDuration)
                        (implicit ec: ExecutionContext, s: Scheduler, F: Effect[F], ES: EffectScheduler[F]): Stream[F, Unit] = {
    program.onError { err =>
      log.error(s"$err")
      log.info(s"Restarting in $retry...")
      loop[F](Stream.eval(ES.schedule[Unit](program.run, retry)), retry)
    }
  }

}