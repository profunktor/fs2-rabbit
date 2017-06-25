package com.github.gvolpe.fs2rabbit

import cats.effect.{Effect, IO}
import com.github.gvolpe.fs2rabbit.examples.runner.{IOEffectRunner, MonixEffectRunner, ScalazEffectRunner}
import com.github.gvolpe.fs2rabbit.examples.scheduler.{IOEffectScheduler, MonixEffectScheduler, ScalazEffectScheduler}

import scalaz.{-\/, \/, \/-}
import scalaz.concurrent.Task

package object examples {

  implicit val iOEffectScheduler      = IOEffectScheduler
  implicit val ioEffectRunner         = IOEffectRunner
  implicit val monixEffectScheduler   = MonixEffectScheduler
  implicit val monixEffectRunner      = MonixEffectRunner
  implicit val scalazEffectScheduler  = ScalazEffectScheduler
  implicit val scalazEffectRunner     = ScalazEffectRunner

  implicit def disjunctionConverter[A, B](disj: A \/ B): Either[A, B] = disj match {
    case -\/(e) => Left(e)
    case \/-(x) => Right(x)
  }

  implicit val scalazTaskEffect = new Effect[Task] {

    override def runAsync[A](fa: Task[A])(cb: (Either[Throwable, A]) => IO[Unit]) = ???

    override def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): Task[A] = ???

    override def suspend[A](thunk: => Task[A]): Task[A] = Task.suspend(thunk)

    override def pure[A](x: A): Task[A] = Task.now(x)

    override def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: (A) => Task[Either[A, B]]): Task[B] = f(a) flatMap {
      case Left(a2) => tailRecM(a2)(f)
      case Right(b) => pure(b)
    }

    override def raiseError[A](e: Throwable): Task[Nothing] = Task.fail(e)

    override def handleErrorWith[A](fa: Task[A])(f: (Throwable) => Task[A]): Task[A] =
      fa.attempt.flatMap(_.fold(f, pure))

  }

}
