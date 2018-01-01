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
import com.github.gvolpe.fs2rabbit.Fs2Utils.evalF
import com.github.gvolpe.fs2rabbit.algebra.BindingAlg
import com.github.gvolpe.fs2rabbit.model.{ExchangeBindingArgs, ExchangeName, QueueBindingArgs, QueueName, RoutingKey}
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

import scala.collection.JavaConverters._

class BindingProgram[F[_] : Sync] extends BindingAlg[Stream[F, ?]] {

  /**
    * Binds a queue to an exchange, with extra arguments.
    **/
  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey): Stream[F, Queue.BindOk] =
    evalF[F, Queue.BindOk] {
      channel.queueBind(queueName.value, exchangeName.value, routingKey.value)
    }

  /**
    * Binds a queue to an exchange with the given arguments.
    **/
  override def bindQueue(channel: Channel,
                         queueName: QueueName,
                         exchangeName: ExchangeName,
                         routingKey: RoutingKey,
                         args: QueueBindingArgs): Stream[F, Queue.BindOk] =
    evalF[F, Queue.BindOk] {
      channel.queueBind(queueName.value, exchangeName.value, routingKey.value, args.value.asJava)
    }

  /**
    * Binds a queue to an exchange with the given arguments but sets nowait parameter to true and returns
    * nothing (as there will be no response from the server).
    **/
  override def bindQueueNoWait(channel: Channel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey,
                               args: QueueBindingArgs): Stream[F, Unit] =
    evalF[F, Unit] {
      channel.queueBindNoWait(queueName.value, exchangeName.value, routingKey.value, args.value.asJava)
    }

  /**
    * Unbinds a queue from an exchange with the given arguments.
    **/
  override def unbindQueue(channel: Channel,
                           queueName: QueueName,
                           exchangeName: ExchangeName,
                           routingKey: RoutingKey): Stream[F, Queue.UnbindOk] =
    evalF[F, Queue.UnbindOk] {
      channel.queueUnbind(queueName.value, exchangeName.value, routingKey.value)
    }

  /**
    * Binds an exchange to an exchange.
    **/
  override def bindExchange(channel: Channel,
                            destination: ExchangeName,
                            source: ExchangeName,
                            routingKey: RoutingKey,
                            args: ExchangeBindingArgs): Stream[F, Exchange.BindOk] =
    evalF[F, Exchange.BindOk]{
      channel.exchangeBind(destination.value, source.value, routingKey.value, args.value.asJava)
    }
}
