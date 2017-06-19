package com.github.gvolpe.fs2rabbit

import scala.language.higherKinds

/**
  * A generic unsafe synchronously runner representation for any [[cats.effect.Effect]].
  *
  * The reasons behind having this type is just to use it only once in your program as
  * part of generic solution for any effect. Some types already implement this method.
  *
  * For example the [[cats.effect.IO.unsafeRunSync()]].
  *
  * When having a Stream defined as your whole program, at the "end of the universe" you
  * will do this for example:
  *
  * {{{
  * import cats.effect.IO
  * import fs2._
  *
  * val program = Stream.eval(IO { println("Hey!") })
  *
  * program.run.unsafeRunSync
  * }}}
  *
  * Having this generic type allows the fs2-rabbit library to to this for any effect:
  *
  * {{{
  * import cats.effect.Effect
  * import fs2._
  *
  * def createProgram[F[_]](implicit F: Effect[F], R: EffectUnsafeSyncRunner[F]) = {
  *   Stream.eval(F.delay { println("hey") })
  * }
  *
  * def run[F[_]](program: Stream[F, Unit])(implicit F: Effect[F], R: EffectUnsafeSyncRunner[F]) =
  *   R.unsafeRunSync(program.run)
  *
  * run[IO](createProgram[IO])
  * }}}
  *
  * Its only intention is to be used together with [[StreamLoop]]
  * */
trait EffectUnsafeSyncRunner[F[_]] {
  /**
    * Produces the result by running the encapsulated effects as impure
    * side effects.
    * */
  def unsafeRunSync(effect: F[Unit]): Unit
}

object EffectUnsafeSyncRunner {
  def apply[F[_] : EffectUnsafeSyncRunner]: EffectUnsafeSyncRunner[F] = implicitly[EffectUnsafeSyncRunner[F]]
}