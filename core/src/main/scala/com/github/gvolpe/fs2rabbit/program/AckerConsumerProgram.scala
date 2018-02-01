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

import cats.effect.{Async, IO}
import com.github.gvolpe.fs2rabbit.algebra.{AMQPClient, AckerConsumer}
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.typeclasses.StreamEval
import com.rabbitmq.client.Channel
import fs2.async.mutable
import fs2.{Pipe, Sink, Stream}

class AckerConsumerProgram[F[_]](
    internalQ: mutable.Queue[IO, Either[Throwable, AmqpEnvelope]],
    config: F[Fs2RabbitConfig])(implicit F: Async[F], SE: StreamEval[F], AMQP: AMQPClient[Stream[F, ?]])
    extends AckerConsumer[Stream[F, ?], Sink[F, ?]] {

  private def resilientConsumer: Pipe[F, Either[Throwable, AmqpEnvelope], AmqpEnvelope] =
    _.flatMap {
      case Left(err)  => Stream.raiseError(err)
      case Right(env) => SE.evalF[AmqpEnvelope](env)
    }

  override def createAcker(channel: Channel): Sink[F, AckResult] =
    _.flatMap {
      case Ack(tag) => AMQP.basicAck(channel, tag, multiple = false)
      case NAck(tag) =>
        Stream.eval(config).flatMap(c => AMQP.basicNack(channel, tag, multiple = false, c.requeueOnNack))
    }

  override def createConsumer(queueName: QueueName,
                              channel: Channel,
                              basicQos: BasicQos,
                              autoAck: Boolean = false,
                              noLocal: Boolean = false,
                              exclusive: Boolean = false,
                              consumerTag: String = "",
                              args: Map[String, AnyRef] = Map.empty[String, AnyRef]): StreamConsumer[F] =
    for {
      _        <- AMQP.basicQos(channel, basicQos)
      _        <- AMQP.basicConsume(channel, queueName, autoAck, consumerTag, noLocal, exclusive, args)
      consumer <- Stream.repeatEval(internalQ.dequeue1.to[F]) through resilientConsumer
    } yield consumer

}
