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

package com.github.gvolpe.fs2rabbit

import com.github.gvolpe.fs2rabbit.model.{AmqpHeaderVal, IntVal, LongVal, StringVal}
import org.scalatest.{FlatSpecLike, Matchers}

class AmqpHeaderValSpec extends FlatSpecLike with Matchers {

  it should "convert from and to Java primitive header values" in {
    val intVal    = IntVal(1)
    val longVal   = LongVal(2L)
    val stringVal = StringVal("hey")

    AmqpHeaderVal.from(intVal.impure)     should be (intVal)
    AmqpHeaderVal.from(longVal.impure)    should be (longVal)
    AmqpHeaderVal.from(stringVal.impure)  should be (stringVal)
    AmqpHeaderVal.from("fs2")             should be (StringVal("fs2"))
  }

}
