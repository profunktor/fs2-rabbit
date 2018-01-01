/*
 * Copyright 2017 Fs2 Rabbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gvolpe.fs2rabbit.typeclasses

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
  * A generic scheduler representation for any [[cats.effect.Effect]] that is able to schedule
  * effects to run later on.
  *
  * Some effects like the Monix Task and the Scalaz Task have support for scheduling effects.
  * With this abstraction, we can do it generically for any given effect.
  * */
trait EffectScheduler[F[_]] {
  /**
    * It creates an Effect that will be submitted for execution after the given delay.
    * */
  def schedule[A](effect: F[A], delay: FiniteDuration)(implicit ec: ExecutionContext): F[A]
}

object EffectScheduler {
  def apply[F[_] : EffectScheduler]: EffectScheduler[F] = implicitly[EffectScheduler[F]]
}