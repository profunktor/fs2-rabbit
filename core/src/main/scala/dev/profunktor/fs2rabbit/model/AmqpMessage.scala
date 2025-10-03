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

import cats.*
import cats.data.*
import cats.implicits.*
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import java.nio.charset.StandardCharsets.UTF_8

case class AmqpMessage[A](payload: A, properties: AmqpProperties)
object AmqpMessage {
  implicit def stringEncoder[F[_]: Applicative]: MessageEncoder[F, String] =
    Kleisli { str =>
      AmqpMessage(str.getBytes(UTF_8), AmqpProperties.empty.copy(contentEncoding = Some(UTF_8.name()))).pure[F]
    }
}
