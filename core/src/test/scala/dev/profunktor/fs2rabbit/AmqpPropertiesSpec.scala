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

package dev.profunktor.fs2rabbit

import com.rabbitmq.client.AMQP
import dev.profunktor.fs2rabbit.model.{AmqpProperties, Headers}
import dev.profunktor.fs2rabbit.testing.AmqpPropertiesArbs
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks as PropertyChecks

class AmqpPropertiesSpec extends AnyFlatSpecLike with PropertyChecks with Matchers {

  import AmqpPropertiesArbs.*

  it should s"convert from and to Java AMQP.BasicProperties" in
    forAll { (amqpProperties: AmqpProperties) =>
      val basicProps = amqpProperties.asBasicProps
      AmqpProperties.unsafeFrom(basicProps) should be(amqpProperties)
    }

  it should "create an empty amqp properties" in {
    AmqpProperties.empty should be(
      AmqpProperties(
        contentType = None,
        contentEncoding = None,
        priority = None,
        deliveryMode = None,
        correlationId = None,
        messageId = None,
        `type` = None,
        userId = None,
        appId = None,
        expiration = None,
        replyTo = None,
        clusterId = None,
        timestamp = None,
        headers = Headers.empty
      )
    )
  }

  it should "handle null values in Java AMQP.BasicProperties" in {
    val basic = new AMQP.BasicProperties()
    AmqpProperties.unsafeFrom(basic) should be(AmqpProperties.empty)
  }

}
