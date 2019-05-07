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

import cats.Applicative
import dev.profunktor.fs2rabbit.algebra.{AMQPClient, Acking}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.model.AckResult.{Ack, NAck}
import dev.profunktor.fs2rabbit.model._
import com.rabbitmq.client.Channel

class AckingProgram[F[_]: Applicative](config: Fs2RabbitConfig, AMQP: AMQPClient[F]) extends Acking[F] {

  def createAcker(channel: Channel): F[AckResult => F[Unit]] =
    Applicative[F].pure {
      case Ack(tag)  => AMQP.basicAck(channel, tag, multiple = false)
      case NAck(tag) => AMQP.basicNack(channel, tag, multiple = false, config.requeueOnNack)
    }

}
