/*
 * Copyright 2017-2019 Gabriel Volpe
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

package com.github.gvolpe.fs2rabbit.algebra

import com.github.gvolpe.fs2rabbit.arguments.Arguments
import com.github.gvolpe.fs2rabbit.effects.EnvelopeDecoder
import com.github.gvolpe.fs2rabbit.model.{AmqpEnvelope, BasicQos, ConsumerTag, QueueName}
import com.rabbitmq.client.Channel

trait Consuming[F[_], R[_]] {

  def createConsumer[A](
      queueName: QueueName,
      channel: Channel,
      basicQos: BasicQos,
      autoAck: Boolean = false,
      noLocal: Boolean = false,
      exclusive: Boolean = false,
      consumerTag: ConsumerTag = ConsumerTag(""),
      args: Arguments = Map.empty
  )(implicit decoder: EnvelopeDecoder[F, A]): F[R[AmqpEnvelope[A]]]

}
