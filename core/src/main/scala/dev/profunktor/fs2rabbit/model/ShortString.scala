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

package dev.profunktor.fs2rabbit.model

import cats.Order

/** A string whose UTF-8 encoded representation is 255 bytes or less.
  *
  * Parts of the AMQP spec call for the use of such strings.
  */
sealed abstract case class ShortString private (str: String)
object ShortString {
  val MaxByteLength = 255

  def from(str: String): Option[ShortString] =
    if (str.getBytes("utf-8").length <= MaxByteLength) {
      Some(new ShortString(str) {})
    } else {
      None
    }

  /** This bypasses the safety check that [[from]] has. This is meant only for situations where you are certain the
    * string cannot be larger than [[MaxByteLength]] (e.g. string literals).
    */
  def unsafeFrom(str: String): ShortString = new ShortString(str) {}

  implicit val shortStringOrder: Order[ShortString]       = Order.by(_.str)
  implicit val shortStringOrdering: Ordering[ShortString] = Order[ShortString].toOrdering
}
