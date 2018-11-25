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

import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monadError._
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, AMQPInternals, Consuming, InternalQueue}
import com.github.gvolpe.fs2rabbit.arguments.Arguments
import com.github.gvolpe.fs2rabbit.effects.EnvelopeDecoder
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.Channel
import fs2.Stream

class ConsumingProgram[F[_]: MonadError[?[_], Throwable]](AMQP: AMQPClient[Stream[F, ?], F], IQ: InternalQueue[F])
    extends Consuming[Stream[F, ?], F] {

  override def createConsumer[A](
      queueName: QueueName,
      channel: Channel,
      basicQos: BasicQos,
      autoAck: Boolean = false,
      noLocal: Boolean = false,
      exclusive: Boolean = false,
      consumerTag: ConsumerTag = ConsumerTag(""),
      args: Arguments = Map.empty
  )(implicit decoder: EnvelopeDecoder[F, A]): Stream[F, AmqpEnvelope[A]] =
    for {
      internalQ <- Stream.eval(IQ.create)
      internals = AMQPInternals[F](Some(internalQ))
      _         <- AMQP.basicQos(channel, basicQos)
      consumeF  = AMQP.basicConsume(channel, queueName, autoAck, consumerTag, noLocal, exclusive, args)(internals)
      _         <- Stream.bracket(consumeF)(tag => AMQP.basicCancel(channel, tag))
      consumer <- Stream.repeatEval(
                   internalQ.dequeue1.rethrow.flatMap(env => decoder(env).map(a => env.copy(payload = a))))
    } yield consumer
}
