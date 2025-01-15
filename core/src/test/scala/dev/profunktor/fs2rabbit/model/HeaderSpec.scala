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

package dev.profunktor.fs2rabbit.model

import dev.profunktor.fs2rabbit.model.Headers.MissingHeader
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success, Try}

class HeaderSpec extends AnyFunSuite with Matchers {

  test("HeaderKey syntax should allow to create a Headers") {
    Headers(
      "key" -> "value".asAmqpFieldValue
    ) shouldEqual Headers(
      "key" := "value"
    )
  }

  test("Headers from-to Map should be consistent") {
    Headers(
      Map(
        "key1" -> 1.asAmqpFieldValue,
        "key2" -> 2.asAmqpFieldValue
      )
    ).toMap shouldEqual
      Map(
        "key1" -> 1.asAmqpFieldValue,
        "key2" -> 2.asAmqpFieldValue
      )
  }

  test("Headers keys are unique") {
    Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> 2.asAmqpFieldValue
    ).updated("key2", 10) shouldEqual Headers(
      "key1" := 1,
      "key2" := 10
    )
  }

  test("Header exists should return true if the key exists") {
    Headers(
      "key1" -> 1.asAmqpFieldValue
    ).exists(t => t._1 == "key1") shouldEqual true

    Headers(
      "key1" -> 1.asAmqpFieldValue
    ).exists(t => t._1 == "key2") shouldEqual false
  }

  test("Header contains should return true if the key exists") {
    Headers(
      "key1" -> 1.asAmqpFieldValue
    ).contains("key1") shouldEqual true

    Headers(
      "key1" -> 1.asAmqpFieldValue
    ).contains("key2") shouldEqual false
  }

  // add
  test("Headers updated should works as expected") {
    Headers(
      "key1" -> 1.asAmqpFieldValue
    ).updated("key1", 10).updated("key2", 20) shouldEqual Headers(
      "key1" := 10,
      "key2" := 20
    )

    Headers(
      "key1" -> 1.asAmqpFieldValue
    ) + ("key1" -> 10.asAmqpFieldValue) shouldEqual Headers(
      "key1" := 10
    )
  }

  test("Headers ++ should works as expected") {
    Headers(
      "key1" -> 1.asAmqpFieldValue
    ) ++ Headers(
      "key2" := 2,
      "key3" := 3
    ) shouldEqual Headers(
      "key1" := 1,
      "key2" := 2,
      "key3" := 3
    )
  }

  // get-as
  test("Headers get should return the value if it exists as expected") {

    val headers: Headers = Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> "value".asAmqpFieldValue
    )

    headers.get[Try]("key1") shouldEqual Success(1.asAmqpFieldValue)
    headers.get[Try]("key2") shouldEqual Success("value".asAmqpFieldValue)
    headers.get[Try]("key3") shouldEqual Failure(MissingHeader("key3"))
  }

  test("Headers getOpt should return the value if it exists as expected") {

    val headers: Headers = Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> "value".asAmqpFieldValue
    )

    headers.getOpt("key1") shouldEqual Some(1.asAmqpFieldValue)
    headers.getOpt("key2") shouldEqual Some("value".asAmqpFieldValue)
    headers.getOpt("key3") shouldEqual None
  }

  test("Headers getAs should return the value if it exists decoded as expected") {

    val headers: Headers = Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> "value".asAmqpFieldValue
    )

    headers.getAs[Try, Int]("key1") shouldEqual Success(1)
    headers.getAs[Try, String]("key2") shouldEqual Success("value")
    headers.getAs[Try, String]("key3") shouldEqual Failure(MissingHeader("key3"))
  }

  test("Headers getOptAs should return the value if it exists decoded as expected") {

    val headers: Headers = Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> "value".asAmqpFieldValue
    )

    headers.getOptAs[Int]("key1") shouldEqual Some(1)
    headers.getOptAs[String]("key2") shouldEqual Some("value")
    headers.getOptAs[String]("key3") shouldEqual None
  }

  // remove
  test("Headers remove should works as expected") {
    Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> 2.asAmqpFieldValue,
      "key3" -> 3.asAmqpFieldValue
    ).remove("key1", "key2") shouldEqual Headers(
      "key3" := 3
    )

    Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> 2.asAmqpFieldValue
    ) - "key1" shouldEqual Headers(
      "key2" := 2
    )
  }

  test("Headers -- should works as expected") {
    Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> 2.asAmqpFieldValue
    ) -- Headers(
      "key1" := 1
    ) shouldEqual Headers(
      "key2" := 2
    )
  }

  test("Headers eq works as expected") {
    Headers(
      "key1" -> 1.asAmqpFieldValue,
      "key2" -> 2.asAmqpFieldValue
    ) shouldEqual Headers(
      "key1" := 1,
      "key2" := 2
    )

    assert(
      Headers(
        "key1" -> 1.asAmqpFieldValue,
        "key2" -> 2.asAmqpFieldValue
      ) == Headers(
        "key1" := 1,
        "key2" := 2
      )
    )
  }

  test("Headers.empty should be an empty Map") {
    Headers.empty.toMap shouldEqual Map.empty
  }
}
