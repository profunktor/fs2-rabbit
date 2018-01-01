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

import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.algebra.DeclarationAlg
import com.github.gvolpe.fs2rabbit.model.{ExchangeName, QueueName}
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.typeclasses.StreamEval
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

import scala.collection.JavaConverters._

class DeclarationProgram[F[_] : Sync](implicit SE: StreamEval[F]) extends DeclarationAlg[Stream[F, ?]] {

  override def declareExchange(channel: Channel,
                               exchangeName: ExchangeName,
                               exchangeType: ExchangeType): Stream[F, Exchange.DeclareOk] =
    SE.evalF[Exchange.DeclareOk] {
      channel.exchangeDeclare(exchangeName.value, exchangeType.toString.toLowerCase)
    }

  override def declareQueue(channel: Channel, queueName: QueueName): Stream[F, Queue.DeclareOk] =
    SE.evalF[Queue.DeclareOk] {
      channel.queueDeclare(queueName.value, false, false, false, Map.empty[String, AnyRef].asJava)
    }

}
