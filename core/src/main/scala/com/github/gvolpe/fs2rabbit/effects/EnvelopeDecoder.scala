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

package com.github.gvolpe.fs2rabbit.effects

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8

import cats.ApplicativeError
import com.github.gvolpe.fs2rabbit.model.AmqpProperties

/**
  * Typeclass that provides the machinery to decode a given AMQP Envelope's payload.
  *
  * There's a default instance for decoding payloads into a UTF-8 String.
  * */
trait EnvelopeDecoder[F[_], A] {
  def decode(raw: Array[Byte], properties: AmqpProperties): F[A]
}

object EnvelopeDecoder {
  def apply[F[_], A](implicit ev: EnvelopeDecoder[F, A]): EnvelopeDecoder[F, A] = ev

  implicit def utf8StringDecoder[F[_]](implicit F: ApplicativeError[F, Throwable]): EnvelopeDecoder[F, String] =
    new EnvelopeDecoder[F, String] {
      override def decode(raw: Array[Byte], properties: AmqpProperties): F[String] = {
        val encoding = properties.contentEncoding.fold(UTF_8)(Charset.forName)
        F.catchNonFatal(new String(raw, encoding))
      }
    }
}
