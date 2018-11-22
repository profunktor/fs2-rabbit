/*
 * Copyright 2017-2019 Fs2 Rabbit
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

import com.github.gvolpe.fs2rabbit.algebra.{AckConsuming, Acking, Consuming}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.effects.{EnvelopeDecoder, StreamEval}
import com.rabbitmq.client.Channel
import fs2.Stream

class AckConsumingProgram[F[_]](A: Acking[F], C: Consuming[Stream[F, ?], F])(implicit SE: StreamEval[F])
    extends AckConsuming[Stream[F, ?], F] {

  override def createAckerConsumer[A](
      channel: Channel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): Stream[F, (AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] = {
    val consumer = consumerArgs.fold(C.createConsumer(queueName, channel, basicQos)) { args =>
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
    Stream.eval(A.createAcker(channel)).map((_, consumer))
  }

  override def createAutoAckConsumer[A](
      channel: Channel,
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit decoder: EnvelopeDecoder[F, A]): Stream[F, Stream[F, AmqpEnvelope[A]]] = {
    val consumer = consumerArgs.fold(C.createConsumer(queueName, channel, basicQos, autoAck = true)) { args =>
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
    SE.pure(consumer)
  }

}
