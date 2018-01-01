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

import cats.effect.Async
import com.github.gvolpe.fs2rabbit.Fs2Utils._
import com.github.gvolpe.fs2rabbit.algebra.{AmqpClientAlg, ConsumingAlg}
import com.github.gvolpe.fs2rabbit.model.{BasicQos, ConsumerArgs, QueueName, StreamAcker, StreamConsumer}
import com.rabbitmq.client.Channel
import fs2.{Sink, Stream}

class ConsumingProgram[F[_] : Async](implicit C: AmqpClientAlg[Stream[F, ?], Sink[F, ?]]) extends ConsumingAlg[Stream[F, ?], Sink[F, ?]] {

  override def createAckerConsumer(channel: Channel,
                                   queueName: QueueName,
                                   basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                                   consumerArgs: Option[ConsumerArgs] = None): Stream[F, (StreamAcker[F], StreamConsumer[F])] = {
    val consumer = consumerArgs.fold(C.createConsumer(queueName, channel, basicQos)) { args =>
      C.createConsumer(
        queueName = queueName,
        channel = channel,
        basicQos = basicQos,
        noLocal = args.noLocal,
        exclusive = args.exclusive,
        consumerTag = args.consumerTag,
        args = args.args)
    }
    evalF((C.createAcker(channel), consumer))
  }

  override def createAutoAckConsumer(channel: Channel,
                                     queueName: QueueName,
                                     basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                                     consumerArgs: Option[ConsumerArgs] = None): StreamConsumer[F] = {
    consumerArgs.fold(C.createConsumer(queueName, channel, basicQos, autoAck = true)) { args =>
      C.createConsumer(
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

}
