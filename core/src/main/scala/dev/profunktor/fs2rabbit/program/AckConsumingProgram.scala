/*
 * Copyright 2017-2019 ProfunKtor
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

package dev.profunktor.fs2rabbit.program

import cats.Apply
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.{AckConsuming, Acking, Consuming}
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.model._
import com.rabbitmq.client.Channel
import fs2.Stream

class AckConsumingProgram[F[_]: Apply](A: Acking[F], C: Consuming[F, Stream[F, ?]])
    extends AckConsuming[F, Stream[F, ?]] {

  override def createAckerConsumer[A](
      channel: Channel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): F[(AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] = {
    val makeConsumer =
      consumerArgs.fold(C.createConsumer(queueName, channel, basicQos)) { args =>
        C.createConsumer[A](
          queueName = queueName,
          channel = channel,
          basicQos = basicQos,
          noLocal = args.noLocal,
          exclusive = args.exclusive,
          consumerTag = args.consumerTag,
          args = args.args
        )
      }
    (A.createAcker(channel), makeConsumer).tupled
  }

  override def createAutoAckConsumer[A](
      channel: Channel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] =
    consumerArgs.fold(C.createConsumer(queueName, channel, basicQos, autoAck = true)) { args =>
      C.createConsumer[A](
        queueName = queueName,
        channel = channel,
        basicQos = basicQos,
        autoAck = true,
        noLocal = args.noLocal,
        exclusive = args.exclusive,
        consumerTag = args.consumerTag,
        args = args.args
      )
    }

}
