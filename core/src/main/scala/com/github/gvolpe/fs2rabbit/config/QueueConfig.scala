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

import com.github.gvolpe.fs2rabbit.config.QueueConfig.{AutoDeleteCfg, DurableCfg, ExclusiveCfg}
import com.github.gvolpe.fs2rabbit.model.QueueName

final case class QueueConfig(queueName: QueueName,
                             durable: DurableCfg,
                             exclusive: ExclusiveCfg,
                             autoDelete: AutoDeleteCfg,
                             arguments: Map[String, AnyRef])
object QueueConfig {

  def default(queueName: QueueName): QueueConfig =
    QueueConfig(queueName, NonDurable, NonExclusive, NonAutoDelete, Map.empty)

  sealed trait DurableCfg extends BooleanCfg {
    def asBoolean: Boolean =
      this == Durable
  }
  case object Durable    extends DurableCfg
  case object NonDurable extends DurableCfg

  sealed trait ExclusiveCfg extends BooleanCfg {
    def asBoolean: Boolean =
      this == Exclusive
  }
  case object Exclusive    extends ExclusiveCfg
  case object NonExclusive extends ExclusiveCfg

  sealed trait AutoDeleteCfg extends BooleanCfg {
    def asBoolean: Boolean =
      this == AutoDelete
  }
  case object AutoDelete    extends AutoDeleteCfg
  case object NonAutoDelete extends AutoDeleteCfg

  sealed trait BooleanCfg {
    def asBoolean: Boolean
  }

}
