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
