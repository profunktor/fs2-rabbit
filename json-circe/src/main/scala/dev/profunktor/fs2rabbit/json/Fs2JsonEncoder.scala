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

import dev.profunktor.fs2rabbit.model.AmqpMessage
import io.circe.Encoder
import io.circe.Printer
import io.circe.syntax._

/** Stream-based Json Encoder that exposes only one method as a streaming transformation using `fs2.Pipe` and depends on
  * the Circe library.
  *
  * @param printer
  *   The `io.circe.Printer` to be used to convert to JSON - overwrite if you need different output formatting, for
  *   example, to omit null values.
  */
class Fs2JsonEncoder(printer: Printer = Printer.noSpaces) {

  /** It tries to encode a given case class encapsulated in an `AmqpMessage` into a json string.
    *
    * For example:
    *
    * {{{
    * import fs2._
    *
    * val payload = Person("Sherlock", Address(212, "Baker St"))
    * jsonEncode[Person](AmqpMessage(payload, AmqpProperties.empty))
    * }}}
    *
    * The result will be an `AmqpMessage` of type `String`
    */
  def jsonEncode[A: Encoder]: AmqpMessage[A] => AmqpMessage[String] =
    amqpMsg => AmqpMessage[String](amqpMsg.payload.asJson.printWith(printer), amqpMsg.properties)
}
