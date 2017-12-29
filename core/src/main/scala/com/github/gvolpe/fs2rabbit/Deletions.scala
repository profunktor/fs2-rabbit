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

package com.github.gvolpe.fs2rabbit

import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client.Channel
import fs2.Stream

trait Deletions {

  /**
    * Delete a queue.
    *
    * @param channel the channel where the publisher is going to be created
    * @param queueName the name of the queue
    * @param ifUnused true if the queue should be deleted only if not in use
    * @param ifEmpty true if the queue should be deleted only if empty
    *
    * @return an effectful [[fs2.Stream]]
    * */
  def deleteQueue[F[_] : Sync](channel: Channel,
                               queueName: QueueName,
                               ifUnused: Boolean = true,
                               ifEmpty: Boolean = true): Stream[F, Queue.DeleteOk] =
    asyncF[F, Queue.DeleteOk] {
      channel.queueDelete(queueName.value, ifUnused, ifEmpty)
    }

  /**
    * Delete a queue without waiting for the response from the server.
    *
    * @param channel the channel where the publisher is going to be created
    * @param queueName the name of the queue
    * @param ifUnused true if the queue should be deleted only if not in use
    * @param ifEmpty true if the queue should be deleted only if empty
    *
    * @return an effectful [[fs2.Stream]]
    * */
  def deleteQueueNoWait[F[_] : Sync](channel: Channel,
                                     queueName: QueueName,
                                     ifUnused: Boolean = true,
                                     ifEmpty: Boolean = true): Stream[F,Unit] =
    asyncF[F, Unit] {
      channel.queueDeleteNoWait(queueName.value, ifUnused, ifEmpty)
    }

}
