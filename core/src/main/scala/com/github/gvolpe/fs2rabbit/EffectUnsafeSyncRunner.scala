package com.github.gvolpe.fs2rabbit

import scala.language.higherKinds

/**
  * A generic unsafe synchronously runner representation for any [[cats.effect.Effect]].
  *
  * The reasons behind having this type is just to use it only once in your program as
  * part of generic solution for any effect. Some types already implement this method:
  *
  * @see [[cats.effect.IO.unsafeRunSync()]]
  *
  * When having a Stream defined as your whole program, at the "end of the universe" you
  * will do this for example:
  *
  * {{{
  * val program = Stream.eval(IO { println("Hey!") })
  *
  * program.run.unsafeRunSync
  * }}}
  *
  * Having this generic type allows the fs2-rabbit library to to this for any effect:
  *
  * {{{
  * def createProgram[F[_]](implicit F: Effect[F], ES: EffectUnsafeSyncRunner[F]) = {
  *   Stream.eval(F.delay { println("hey") })
  * }
  *
  * def run[F[_]](program: Stream[F, Unit])(implicit F: Effect[F], ES: EffectUnsafeSyncRunner[F]) =
  *   ES.unsafeRunSync(program.run)
  *
  * run[IO](createProgram[IO])
  * }}}
  *
  * Its only intention is to be used together with [[StreamLoop]]
  * */
trait EffectUnsafeSyncRunner[F[_]] {
  def unsafeRunSync(effect: F[Unit]): Unit
}
