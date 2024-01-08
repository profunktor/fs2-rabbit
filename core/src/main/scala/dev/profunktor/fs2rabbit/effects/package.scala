/*
 * Copyright 2017-2024 ProfunKtor
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
import cats.data.Kleisli
import dev.profunktor.fs2rabbit.model.{AmqpEnvelope, AmqpMessage}

package object effects {

  /** Typeclass that provides the machinery to decode a given AMQP Envelope's payload.
    *
    * There's a default instance for decoding payloads into a UTF-8 String.
    */
  type EnvelopeDecoder[F[_], A] = Kleisli[F, AmqpEnvelope[Array[Byte]], A]

  type MessageEncoder[F[_], A] = Kleisli[F, A, AmqpMessage[Array[Byte]]]
}
