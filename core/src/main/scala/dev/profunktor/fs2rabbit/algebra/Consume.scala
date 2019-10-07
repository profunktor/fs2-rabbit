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

package dev.profunktor.fs2rabbit.algebra

import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.model._

object Consume {
  def apply[F[_]](implicit ev: Consume[F]): Consume[F] = ev
}

trait Consume[F[_]] {
  def basicAck(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean): F[Unit]
  def basicNack(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit]
  def basicQos(channel: AMQPChannel, basicQos: BasicQos): F[Unit]
  def basicConsume[A](channel: AMQPChannel,
                      queueName: QueueName,
                      autoAck: Boolean,
                      consumerTag: ConsumerTag,
                      noLocal: Boolean,
                      exclusive: Boolean,
                      args: Arguments)(internals: AMQPInternals[F]): F[ConsumerTag]
  def basicCancel(channel: AMQPChannel, consumerTag: ConsumerTag): F[Unit]
}
