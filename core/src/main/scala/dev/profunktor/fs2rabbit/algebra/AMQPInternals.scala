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

package dev.profunktor.fs2rabbit.algebra

import cats.effect.std.Queue
import cats.~>
import dev.profunktor.fs2rabbit.model.AmqpEnvelope

case class AMQPInternals[F[_]](queue: Option[Queue[F, Either[Throwable, AmqpEnvelope[Array[Byte]]]]]) {
  def mapK[G[_]](fK: F ~> G): AMQPInternals[G] = AMQPInternals(queue.map(_.mapK(fK)))
}
