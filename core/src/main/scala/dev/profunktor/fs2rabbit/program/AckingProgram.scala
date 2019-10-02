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
import com.rabbitmq.client.Channel
import dev.profunktor.fs2rabbit.algebra.{Acking, Consume}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.model.AckResult.{Ack, NAck}
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.interpreter.ConsumeEffect
import cats.effect.Effect

object AckingProgram {
  def apply[F[_]: Applicative: Effect](configuration: Fs2RabbitConfig): Acking[F] =
    new AckingProgram[F] with ConsumeEffect[F] {
      override lazy val config: Fs2RabbitConfig     = configuration
      override lazy val applicative: Applicative[F] = Applicative[F]
      override lazy val effect: Effect[F]           = Effect[F]
    }
}

trait AckingProgram[F[_]] extends Acking[F] { consume: Consume[F] =>

  val config: Fs2RabbitConfig
  implicit val applicative: Applicative[F]

  def createAcker(channel: Channel): F[AckResult => F[Unit]] =
    Applicative[F].pure {
      case Ack(tag) => consume.basicAck(channel, tag, multiple = false)
      case NAck(tag) =>
        consume.basicNack(channel, tag, multiple = false, config.requeueOnNack)
    }

}
