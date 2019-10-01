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

package dev.profunktor.fs2rabbit.interpreter

import cats.effect.syntax.effect._
import cats.effect.{Blocker, ContextShift, Effect, Sync}
import cats.syntax.functor._
import com.rabbitmq.client.{AMQP, Channel, ReturnListener}
import dev.profunktor.fs2rabbit.algebra.Publish
import dev.profunktor.fs2rabbit.model._

class PublishEffect[F[_]: Effect: ContextShift](blocker: Blocker) extends Publish[F] {
  override def basicPublish(channel: Channel,
                            exchangeName: ExchangeName,
                            routingKey: RoutingKey,
                            msg: AmqpMessage[Array[Byte]]): F[Unit] =
    blocker.delay {
      channel.basicPublish(
        exchangeName.value,
        routingKey.value,
        msg.properties.asBasicProps,
        msg.payload
      )
    }

  override def basicPublishWithFlag(channel: Channel,
                                    exchangeName: ExchangeName,
                                    routingKey: RoutingKey,
                                    flag: PublishingFlag,
                                    msg: AmqpMessage[Array[Byte]]): F[Unit] =
    blocker.delay {
      channel.basicPublish(
        exchangeName.value,
        routingKey.value,
        flag.mandatory,
        msg.properties.asBasicProps,
        msg.payload
      )
    }

  override def addPublishingListener(
      channel: Channel,
      listener: PublishReturn => F[Unit]
  ): F[Unit] =
    Sync[F].delay {
      val returnListener = new ReturnListener {
        override def handleReturn(replyCode: Int,
                                  replyText: String,
                                  exchange: String,
                                  routingKey: String,
                                  properties: AMQP.BasicProperties,
                                  body: Array[Byte]): Unit = {
          val publishReturn =
            PublishReturn(
              ReplyCode(replyCode),
              ReplyText(replyText),
              ExchangeName(exchange),
              RoutingKey(routingKey),
              AmqpProperties.unsafeFrom(properties),
              AmqpBody(body)
            )

          listener(publishReturn).toIO.unsafeRunAsync(_ => ())
        }
      }

      channel.addReturnListener(returnListener)
    }.void

  override def clearPublishingListeners(channel: Channel): F[Unit] =
    Sync[F].delay {
      channel.clearReturnListeners()
    }.void

}
