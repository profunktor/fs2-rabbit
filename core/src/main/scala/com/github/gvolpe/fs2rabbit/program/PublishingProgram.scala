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

import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, Publishing}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.effects.StreamEval
import com.rabbitmq.client.Channel
import fs2.Stream

class PublishingProgram[F[_], A](AMQP: AMQPClient[Stream[F, ?], F, A])(implicit SE: StreamEval[F])
    extends Publishing[Stream[F, ?], F] {

  override def createPublisher(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  ): Stream[F, StreamPublisher[F]] =
    SE.evalF {
      _.flatMap { msg =>
        AMQP.basicPublish(channel, exchangeName, routingKey, msg)
      }
    }

  override def createPublisherWithListener(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      listener: PublishingListener[F]
  ): Stream[F, StreamPublisher[F]] =
    SE.evalF { publisher =>
      AMQP.addPublishingListener(channel, listener) ++ publisher.flatMap { msg =>
        AMQP.basicPublishWithFlag(channel, exchangeName, routingKey, flag, msg)
      }
    }

}
