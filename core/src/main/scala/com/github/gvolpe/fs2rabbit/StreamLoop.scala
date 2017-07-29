package com.github.gvolpe.fs2rabbit

import cats.effect.Sync
import fs2.Stream
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.higherKinds

/**
  * It provides a resilient run method for an effectful [[fs2.Stream]] that will run forever with
  * automatic error recovery.
  *
  * In case of failure, the entire stream will be restarted after the specified retry time.
  *
  * @see the StreamLoopSpec that demonstrates an use case.
  * */
object StreamLoop {

  private val log = LoggerFactory.getLogger(getClass)

  def run[F[_] : Sync : EffectScheduler : EffectUnsafeSyncRunner](program: () => Stream[F, Unit], retry: FiniteDuration = 5.seconds)
         (implicit ec: ExecutionContext): Unit = {
    EffectUnsafeSyncRunner[F].unsafeRunSync(loop(program(), retry).run)
  }

  private def loop[F[_] : Sync : EffectScheduler](program: Stream[F, Unit], retry: FiniteDuration)
                        (implicit ec: ExecutionContext): Stream[F, Unit] = {
    program.onError { err =>
      log.error(s"$err")
      log.info(s"Restarting in $retry...")
      loop[F](Stream.eval(EffectScheduler[F].schedule[Unit](program.run, retry)), retry)
    }
  }

}