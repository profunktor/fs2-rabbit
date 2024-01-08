/*
 * Copyright 2017-2024 ProfunKtor
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

package dev.profunktor.fs2rabbit.effects

import dev.profunktor.fs2rabbit.config.declaration._
import dev.profunktor.fs2rabbit.config.deletion.{Empty, IfEmptyCfg, IfUnusedCfg, Unused}

trait BoolValue[A] {
  def isTrue(a: A): Boolean
  def isFalse(a: A): Boolean = !isTrue(a)
}

object BoolValue {

  def apply[A](isTrueF: A => Boolean): BoolValue[A] = new BoolValue[A] {
    def isTrue(a: A): Boolean = isTrueF(a)
  }

  implicit def durableCfg: BoolValue[DurableCfg] =
    BoolValue[DurableCfg](cfg => cfg == Durable)

  implicit def exclusiveCfg: BoolValue[ExclusiveCfg] =
    BoolValue[ExclusiveCfg](cfg => cfg == Exclusive)

  implicit def autoDeleteCfg: BoolValue[AutoDeleteCfg] =
    BoolValue[AutoDeleteCfg](cfg => cfg == AutoDelete)

  implicit def internalCfg: BoolValue[InternalCfg] =
    BoolValue[InternalCfg](cfg => cfg == Internal)

  implicit def ifUnusedCfg: BoolValue[IfUnusedCfg] =
    BoolValue[IfUnusedCfg](cfg => cfg == Unused)

  implicit def ifEmptyCfg: BoolValue[IfEmptyCfg] =
    BoolValue[IfEmptyCfg](cfg => cfg == Empty)

  object syntax {
    implicit class BoolValueOps[A: BoolValue](a: A) {
      def isTrue: Boolean = implicitly[BoolValue[A]].isTrue(a)
    }
  }
}
