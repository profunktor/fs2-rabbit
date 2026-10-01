/*
 * Copyright 2017-2026 ProfunKtor
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

package dev.profunktor.fs2rabbit.otel4s

import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{IntVal, StringVal}
import dev.profunktor.fs2rabbit.model.Headers
import dev.profunktor.fs2rabbit.otel4s.instances.*
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.typelevel.otel4s.context.propagation.{TextMapGetter, TextMapUpdater}

class HeadersPropagationSpec extends AnyFlatSpecLike with Matchers {

  it should "replace a propagation header and retain unrelated headers" in {
    val initial = Headers(
      "traceparent" -> StringVal("old"),
      "application" -> IntVal(42)
    )

    val updated = TextMapUpdater[Headers].updated(initial, "traceparent", "new")

    TextMapGetter[Headers].get(updated, "traceparent") shouldBe Some("new")
    updated.getOpt("application") shouldBe Some(IntVal(42))
  }

  it should "ignore non-string values during propagation extraction" in {
    val headers = Headers("traceparent" -> IntVal(42))

    TextMapGetter[Headers].get(headers, "traceparent") shouldBe None
  }
}
