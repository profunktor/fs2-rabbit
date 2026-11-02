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

import dev.profunktor.fs2rabbit.model.AmqpFieldValue.StringVal
import dev.profunktor.fs2rabbit.model.Headers
import org.typelevel.otel4s.context.propagation.{TextMapGetter, TextMapUpdater}

trait Otel4sInstances {

  implicit val headersTextMapGetter: TextMapGetter[Headers] =
    new TextMapGetter[Headers] {
      override def get(carrier: Headers, key: String): Option[String] =
        carrier.getOpt(key).collect { case StringVal(value) => value }

      override def keys(carrier: Headers): Iterable[String] =
        carrier.toMap.keys
    }

  implicit val headersTextMapUpdater: TextMapUpdater[Headers] =
    new TextMapUpdater[Headers] {
      override def updated(carrier: Headers, key: String, value: String): Headers =
        Headers(carrier.toMap.updated(key, StringVal(value)))
    }
}

object instances extends Otel4sInstances
