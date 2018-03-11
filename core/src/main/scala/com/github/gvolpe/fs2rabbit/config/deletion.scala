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

package com.github.gvolpe.fs2rabbit.config

import com.github.gvolpe.fs2rabbit.model.QueueName

object deletion {

  final case class DeletionQueueConfig(queueName: QueueName, ifUnused: IfUnusedCfg, ifEmpty: IfEmptyCfg)

  object DeletionQueueConfig {
    def default(queueName: QueueName): DeletionQueueConfig =
      DeletionQueueConfig(queueName, Unused, Empty)
  }

  sealed trait IfEmptyCfg extends Product with Serializable
  case object Empty       extends IfEmptyCfg
  case object NonEmpty    extends IfEmptyCfg

  sealed trait IfUnusedCfg extends Product with Serializable
  case object Unused       extends IfUnusedCfg
  case object Used         extends IfUnusedCfg
}
