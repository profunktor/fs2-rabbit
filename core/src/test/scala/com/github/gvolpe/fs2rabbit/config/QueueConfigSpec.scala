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

package com.github.gvolpe.fs2rabbit.config

import com.github.gvolpe.fs2rabbit.config.QueueConfig._
import org.scalatest.{FlatSpecLike, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

class QueueConfigSpec extends FlatSpecLike with Matchers with QueueConfigFixture {

  forAll(table) { (config, boolValue) =>
    s"$config" should s"return $boolValue as its boolean value" in {
      config.asBoolean shouldBe boolValue
    }

  }
}

trait QueueConfigFixture {
  val table = Table(
    ("configuration", "boolean value"),
    (Durable, true),
    (NonDurable, false),
    (Exclusive, true),
    (NonExclusive, false),
    (AutoDelete, true),
    (NonAutoDelete, false)
  )
}
