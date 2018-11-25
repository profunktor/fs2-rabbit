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

import cats.Applicative
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, Acking}
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model.AckResult.{Ack, NAck}
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel
import fs2.Stream

class AckingProgram[F[_]: Applicative](config: Fs2RabbitConfig, AMQP: AMQPClient[Stream[F, ?], F]) extends Acking[F] {

  def createAcker(channel: Channel): F[AckResult => F[Unit]] =
    Applicative[F].pure {
      case Ack(tag)  => AMQP.basicAck(channel, tag, multiple = false)
      case NAck(tag) => AMQP.basicNack(channel, tag, multiple = false, config.requeueOnNack)
    }

}
