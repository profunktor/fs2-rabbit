/*
 * Copyright 2017-2025 ProfunKtor
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

package dev.profunktor.fs2rabbit.laws

import cats.kernel.laws.discipline.CommutativeSemigroupTests
import dev.profunktor.fs2rabbit.model.DeliveryTag
import dev.profunktor.fs2rabbit.testkit._
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.FunSpecDiscipline

class CommutativeSemigroupInstancesSpec extends AnyFunSpec with FunSpecDiscipline with ScalaCheckPropertyChecks {
  checkAll("DeliveryTag.CommutativeSemigroupLaws", CommutativeSemigroupTests[DeliveryTag].commutativeSemigroup)
}
