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

package dev.profunktor.fs2rabbit.json

import dev.profunktor.fs2rabbit.model.{AmqpEnvelope, DeliveryTag}
import io.circe.parser.decode
import io.circe.{Decoder, Error}

/** Stream-based Json Decoder that exposes only one method as a streaming transformation using `fs2.Pipe` and depends on
  * the Circe library.
  */
class Fs2JsonDecoder {

  /** It tries to decode an `AmqpEnvelope.payload` into a case class determined by the parameter [A].
    *
    * For example:
    *
    * {{{
    * import fs2._
    *
    * val json = """ { "two": "the two" } """
    * val envelope = AmqpEnvelope(1, json, AmqpProperties.empty)
    *
    * jsonDecode[Person](envelope)
    *
    *
    * }}}
    *
    * The result will be a tuple (`Either` of `Error` and `A`, `DeliveryTag`)
    */
  def jsonDecode[A: Decoder]: AmqpEnvelope[String] => (Either[Error, A], DeliveryTag) =
    amqpMsg => (decode[A](amqpMsg.payload), amqpMsg.deliveryTag)

}
