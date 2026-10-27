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

/** Classifies a queue name to its low-cardinality template. */
trait QueueNameTemplateClassifier {

  /** A `None` result indicates that this classifier does not recognize the queue name. */
  def classify(queueName: QueueName): Option[String]

  /** Returns a classifier that tries this classifier first, then `that` if no template is found. */
  def orElse(that: QueueNameTemplateClassifier): QueueNameTemplateClassifier = that match {
    case QueueNameTemplateClassifier.Indeterminate      => this
    case QueueNameTemplateClassifier.Multi(classifiers) => QueueNameTemplateClassifier.Multi(this :: classifiers)
    case _                                              => QueueNameTemplateClassifier.Multi(List(this, that))
  }
}

object QueueNameTemplateClassifier {

  private object Indeterminate extends QueueNameTemplateClassifier {
    override def classify(queueName: QueueName): Option[String] = None

    override def orElse(that: QueueNameTemplateClassifier): QueueNameTemplateClassifier = that
  }

  private final case class Multi(classifiers: List[QueueNameTemplateClassifier]) extends QueueNameTemplateClassifier {
    override def classify(queueName: QueueName): Option[String] =
      classifiers.iterator.map(_.classify(queueName)).collectFirst { case Some(template) => template }

    override def orElse(that: QueueNameTemplateClassifier): QueueNameTemplateClassifier = that match {
      case Indeterminate => this
      case Multi(next)   => Multi(classifiers ++ next)
      case other         => Multi(classifiers :+ other)
    }
  }

  /** A classifier that does not classify any queue names. */
  val indeterminate: QueueNameTemplateClassifier = Indeterminate

  /** Creates a classifier from a partial function. Undefined queue names return `None`. */
  def matching(pf: PartialFunction[QueueName, String]): QueueNameTemplateClassifier = {
    val lifted = pf.lift
    new QueueNameTemplateClassifier {
      override def classify(queueName: QueueName): Option[String] = lifted(queueName)
    }
  }

  /** Classifies RabbitMQ generated queue names beginning with `amq.gen-`. */
  val rabbitMqGeneratedQueue: QueueNameTemplateClassifier =
    matching { case queue if queue.value.startsWith("amq.gen-") => "amq.gen-*" }

  /** Classifies generated queue names beginning with `spring.gen-`. */
  val springGeneratedQueue: QueueNameTemplateClassifier =
    matching { case queue if queue.value.startsWith("spring.gen-") => "spring.gen-*" }

  /** Classifies lowercase canonical UUID queue names. */
  val uuidQueue: QueueNameTemplateClassifier =
    matching {
      case queue if queue.value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") =>
        "{queue_id}"
    }

  /** Classifiers for the generated queue name patterns recognized by default. */
  val default: QueueNameTemplateClassifier =
    rabbitMqGeneratedQueue
      .orElse(springGeneratedQueue)
      .orElse(uuidQueue)
}
