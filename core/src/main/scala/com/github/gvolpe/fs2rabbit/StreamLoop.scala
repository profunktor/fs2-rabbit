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
         (implicit ec: ExecutionContext, s: Scheduler, F: Effect[F], ES: EffectScheduler[F]): Unit =
    loop(program().run, retry)

  private def loop[F[_]](program: F[Unit], retry: FiniteDuration)
                        (implicit ec: ExecutionContext, s: Scheduler, F: Effect[F], ES: EffectScheduler[F]): Unit = {
    ES.unsafeRunSync {
      F.map[Either[Throwable, Unit], Unit](F.attempt(program)){
        case Left(err) =>
          log.error(s"$err, restarting in $retry...")
          loop[F](ES.schedule[Unit](program, retry), retry)
        case Right(()) => ()
      }
    }
  }

}