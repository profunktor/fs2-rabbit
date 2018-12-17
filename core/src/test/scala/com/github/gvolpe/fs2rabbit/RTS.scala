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

/**
  * Runtime Test Suite that makes sure the internal queues are connected when publishing and consuming in order to
  * simulate a running RabbitMQ server. It should run concurrently with every single test.
  * */
package com.github.gvolpe.fs2rabbit

import cats.effect.{ContextShift, IO}
import com.github.gvolpe.fs2rabbit.algebra.AMQPInternals
import cats.effect.concurrent.Ref
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import fs2.concurrent.Queue
import com.github.gvolpe.fs2rabbit.model._

final case class TestWorld(
    connectionOpen: Ref[IO, Boolean],
    queues: Ref[IO, Set[QueueName]],
    exchanges: Ref[IO, Set[ExchangeName]],
    ackerQ: Queue[IO, AckResult],
    binds: Ref[IO, Map[String, ExchangeName]]
)

object EffectAssertion {
  def apply[A](tuple: (Fs2Rabbit[IO], IO[Unit], TestWorld))(fa: (Fs2Rabbit[IO], TestWorld) => IO[A])(
      implicit cs: ContextShift[IO]): Unit = {
    val (rabbit, testSuiteRTS, world) = tuple
    val io = for {
      rts <- testSuiteRTS.start
      _ <- fa(rabbit, world).guarantee(
            rts.cancel
          )
    } yield ()
    io.unsafeRunSync()
  }
}

object RTS {
  def rabbitRTS(
      ref: Ref[IO, AMQPInternals[IO]],
      publishingQ: Queue[IO, Either[Throwable, AmqpEnvelope[Array[Byte]]]]
  ): IO[Unit] =
    ref.get.flatMap { internals =>
      internals.queue.fold(
        rabbitRTS(ref, publishingQ)
      ) { internalQ =>
        for {
          msg <- publishingQ.dequeue1
          _   <- internalQ.enqueue1(msg)
          //_   <- ref.set(AMQPInternals(None))
          _ <- rabbitRTS(ref, publishingQ)
        } yield ()
      }
    }
}
