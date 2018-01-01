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

package com.github.gvolpe.fs2rabbit.program

import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.Fs2Utils.evalF
import com.github.gvolpe.fs2rabbit.algebra.DeletionAlg
import com.github.gvolpe.fs2rabbit.model.QueueName
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.Channel
import fs2.Stream

// TODO: Remove Channel param from all the methods and make it implicit
class DeletionProgram[F[_] : Sync] extends DeletionAlg[Stream[F, ?]] {

  override def deleteQueue(channel: Channel,
                           queueName: QueueName,
                           ifUnused: Boolean = true,
                           ifEmpty: Boolean = true): Stream[F, Queue.DeleteOk] =
    evalF[F, Queue.DeleteOk] {
      channel.queueDelete(queueName.value, ifUnused, ifEmpty)
    }

  /**
    * Deletes a queue without waiting for the server response.
    **/
  override def deleteQueueNoWait(channel: Channel,
                                 queueName: QueueName,
                                 ifUnused: Boolean = true,
                                 ifEmpty: Boolean = true): Stream[F,Unit] =
    evalF[F, Unit] {
      channel.queueDeleteNoWait(queueName.value, ifUnused, ifEmpty)
    }

}
