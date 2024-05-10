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

package dev.profunktor.fs2rabbit.data

import dev.profunktor.fs2rabbit.model.AmqpFieldValue.StringVal
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HeaderSpec extends AnyFunSuite with Matchers {

  test("Header should be a tuple of a String and an AmqpFieldValue") {
    val header: Header = "key" -> StringVal("value")
  }
}
