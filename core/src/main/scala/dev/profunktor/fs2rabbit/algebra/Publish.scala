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

import dev.profunktor.fs2rabbit.model._

object Publish {
  def apply[F[_]](implicit ev: Publish[F]): Publish[F] = ev
}

trait Publish[F[_]] {
  def basicPublish(channel: AMQPChannel,
                   exchangeName: ExchangeName,
                   routingKey: RoutingKey,
                   msg: AmqpMessage[Array[Byte]]): F[Unit]
  def basicPublishWithFlag(channel: AMQPChannel,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey,
                           flag: PublishingFlag,
                           msg: AmqpMessage[Array[Byte]]): F[Unit]
  def addPublishingListener(channel: AMQPChannel, listener: PublishReturn => F[Unit]): F[Unit]
  def clearPublishingListeners(channel: AMQPChannel): F[Unit]
}
