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

import dev.profunktor.fs2rabbit.model.QueueName
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class QueueNameTemplateClassifierSpec extends AnyFlatSpecLike with Matchers {

  it should "try classifiers in order and fall back when the earlier classifier is indeterminate" in {
    val first      = QueueNameTemplateClassifier.matching {
      case queue if queue.value.startsWith("first-") => "first-{id}"
    }
    val second     = QueueNameTemplateClassifier.matching {
      case queue if queue.value.startsWith("second-") => "second-{id}"
    }
    val classifier = first.orElse(second)

    classifier.classify(QueueName("first-123")) shouldBe Some("first-{id}")
    classifier.classify(QueueName("second-456")) shouldBe Some("second-{id}")
    classifier.classify(QueueName("named-queue")) shouldBe None
  }

  it should "provide composable classifiers for generated queue formats" in {
    QueueNameTemplateClassifier.rabbitMqGeneratedQueue.classify(QueueName("amq.gen-random")) shouldBe Some(
      "amq.gen-*"
    )
    QueueNameTemplateClassifier.springGeneratedQueue.classify(QueueName("spring.gen-random")) shouldBe Some(
      "spring.gen-*"
    )
    QueueNameTemplateClassifier.uuidQueue.classify(QueueName("123e4567-e89b-12d3-a456-426614174000")) shouldBe Some(
      "{queue_id}"
    )
    QueueNameTemplateClassifier.default.classify(QueueName("orders")) shouldBe None
  }
}
