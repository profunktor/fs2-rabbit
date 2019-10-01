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

package dev.profunktor.fs2rabbit.examples

import java.util.concurrent.Executors

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.interpreter._
import dev.profunktor.fs2rabbit.program.{AckingProgram, ConsumingProgram}
import dev.profunktor.fs2rabbit.resiliency.ResilientStream

object IOAckerConsumer extends IOApp {

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    nodes = NonEmptyList.one(Fs2RabbitNodeConfig(host = "127.0.0.1", port = 5672)),
    username = Some("guest"),
    password = Some("guest"),
    ssl = false,
    connectionTimeout = 3,
    requeueOnNack = false,
    internalQueueSize = Some(500),
    automaticRecovery = true
  )

  val blockerResource =
    Resource
      .make(IO(Executors.newCachedThreadPool()))(es => IO(es.shutdown()))
      .map(Blocker.liftExecutorService)

  override def run(args: List[String]): IO[ExitCode] =
    blockerResource.use { blocker =>
      ConnectionEffect[IO](config).flatMap { connection =>
        val consumeClient = ConsumeEffect[IO]()
        val internalQ =
          new LiveInternalQueue[IO](config.internalQueueSize.getOrElse(500))
        val consumer = new ConsumingProgram[IO](consumeClient, internalQ)

        ResilientStream
          .runF(
            new AckerConsumerDemo[IO](
              connection,
              PublishEffect[IO](blocker),
              BindingEffect[IO](),
              DeclarationEffect[IO](),
              AckingProgram[IO](consumeClient, config),
              consumer
            ).program
          )
          .as(ExitCode.Success)
      }
    }

}
