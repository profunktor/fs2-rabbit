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

package com.github.gvolpe.fs2rabbit

import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model.{ExchangeName, QueueName}
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

import scala.collection.JavaConverters._

trait Declarations {

  /**
    * Declares an exchange.
    *
    * @param channel the channel where the exchange is going to be declared
    * @param exchangeName the name of the exchange
    * @param exchangeType the exchange type: Direct, FanOut, Headers, Topic.
    *
    * @return an effectful [[fs2.Stream]] of type [[Exchange.DeclareOk]]
    * */
  def declareExchange[F[_] : Sync](channel: Channel,
                                   exchangeName: ExchangeName,
                                   exchangeType: ExchangeType): Stream[F, Exchange.DeclareOk] =
    asyncF[F, Exchange.DeclareOk] {
      channel.exchangeDeclare(exchangeName.value, exchangeType.toString.toLowerCase)
    }

  /**
    * Declares a queue.
    *
    * @param channel the channel where the queue is going to be declared
    * @param queueName the name of the queue
    *
    * @return an effectful [[fs2.Stream]] of type [[Queue.DeclareOk]]
    * */
  def declareQueue[F[_] : Sync](channel: Channel, queueName: QueueName): Stream[F, Queue.DeclareOk] =
    asyncF[F, Queue.DeclareOk] {
      channel.queueDeclare(queueName.value, false, false, false, Map.empty[String, AnyRef].asJava)
    }

}

object Declarations {
  case class QueueDeclarationArgs(value: Map[String, AnyRef])
  case class ExchangeDeclarationArgs(value: Map[String, AnyRef])
}