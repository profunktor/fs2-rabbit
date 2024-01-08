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

import dev.profunktor.fs2rabbit.model.{AMQPChannel, AckMultiple, AckResult}

trait Acking[F[_]] {
  def createAcker(channel: AMQPChannel, ackMultiple: AckMultiple): F[AckResult => F[Unit]]
  def createAckerWithMultipleFlag(channel: AMQPChannel): F[(AckResult, AckMultiple) => F[Unit]]
}
