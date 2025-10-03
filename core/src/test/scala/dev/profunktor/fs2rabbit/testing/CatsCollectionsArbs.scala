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

package dev.profunktor.fs2rabbit.testing

import cats.data.{NonEmptyList, NonEmptySeq}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.*

object CatsCollectionsArbs {

  implicit def arbNel[T: Arbitrary]: Arbitrary[NonEmptyList[T]] = Arbitrary {
    for {
      head <- arbitrary[T]
      tail <- arbitrary[List[T]]
    } yield NonEmptyList(head, tail)
  }

  implicit def arbNes[T: Arbitrary]: Arbitrary[NonEmptySeq[T]] = Arbitrary {
    for {
      head <- arbitrary[T]
      tail <- arbitrary[Vector[T]]
    } yield NonEmptySeq(head, tail)
  }
}
