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

import cats.FlatMap
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, Publishing}
import com.github.gvolpe.fs2rabbit.effects.{MessageEncoder, StreamEval}
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel
import fs2.Stream

class PublishingProgram[F[_]: FlatMap](AMQP: AMQPClient[Stream[F, ?], F])(implicit SE: StreamEval[F])
    extends Publishing[Stream[F, ?], F] {

  override def createPublisher[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit encoder: MessageEncoder[F, A]): Stream[F, A => F[Unit]] =
    SE.pure {
      encoder.flatMapF(AMQP.basicPublish(channel, exchangeName, routingKey, _)).run
    }

  override def createPublisherWithListener[A](
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flag: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit encoder: MessageEncoder[F, A]): Stream[F, A => F[Unit]] =
    AMQP.addPublishingListener(channel, listener).drain ++ SE.pure {
      encoder.flatMapF(AMQP.basicPublishWithFlag(channel, exchangeName, routingKey, flag, _)).run
    }

}
