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

import cats.effect.Bracket
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.{AMQPClient, AMQPInternals, Consuming, InternalQueue}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.model._
import com.rabbitmq.client.Channel
import fs2.Stream

class ConsumingProgram[F[_]: Bracket[?[_], Throwable]](AMQP: AMQPClient[F], IQ: InternalQueue[F])
    extends Consuming[F, Stream[F, ?]] {

  override def createConsumer[A](
      queueName: QueueName,
      channel: Channel,
      basicQos: BasicQos,
      autoAck: Boolean = false,
      noLocal: Boolean = false,
      exclusive: Boolean = false,
      consumerTag: ConsumerTag = ConsumerTag(""),
      args: Arguments = Map.empty
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] = {

    val setup = for {
      internalQ   <- IQ.create
      internals   = AMQPInternals[F](Some(internalQ))
      _           <- AMQP.basicQos(channel, basicQos)
      consumerTag <- AMQP.basicConsume(channel, queueName, autoAck, consumerTag, noLocal, exclusive, args)(internals)
    } yield (consumerTag, internalQ)

    Stream
      .bracket(setup) {
        case (tag, _) =>
          AMQP.basicCancel(channel, tag)
      }
      .flatMap {
        case (_, queue) =>
          Stream.repeatEval(
            queue.dequeue1.rethrow.flatMap(env => decoder(env).map(a => env.copy(payload = a)))
          )
      }
      .pure[F]
  }
}
