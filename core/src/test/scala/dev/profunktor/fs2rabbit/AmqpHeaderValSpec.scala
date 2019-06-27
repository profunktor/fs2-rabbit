/*
 * Copyright 2017-2019 ProfunKtor
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

package dev.profunktor.fs2rabbit

import dev.profunktor.fs2rabbit.model.AmqpHeaderVal
import dev.profunktor.fs2rabbit.model.AmqpHeaderVal._
import org.scalatest.{FlatSpecLike, Matchers}

class AmqpHeaderValSpec extends FlatSpecLike with Matchers with AmqpPropertiesArbitraries {

  it should "convert from and to Java primitive header values" in {
    val intVal    = IntVal(1)
    val longVal   = LongVal(2L)
    val stringVal = StringVal("hey")
    val arrayVal  = ArrayVal(Vector(IntVal(3), IntVal(2), IntVal(1)))

    AmqpHeaderVal.unsafeFrom(intVal.impure) should be(intVal)
    AmqpHeaderVal.unsafeFrom(longVal.impure) should be(longVal)
    AmqpHeaderVal.unsafeFrom(stringVal.impure) should be(stringVal)
    AmqpHeaderVal.unsafeFrom("fs2") should be(StringVal("fs2"))
    AmqpHeaderVal.unsafeFrom(arrayVal.impure) should be(ArrayVal(Vector(IntVal(3), IntVal(2), IntVal(1))))
  }
  it should "have impure and from be opposites of each other" in {
    forAll { amqpHeaderVal: AmqpHeaderVal =>
      AmqpHeaderVal.unsafeFrom(amqpHeaderVal.impure) == amqpHeaderVal
    }
  }

}
