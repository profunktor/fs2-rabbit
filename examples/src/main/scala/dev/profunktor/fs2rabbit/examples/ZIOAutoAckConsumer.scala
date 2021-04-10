/*
 * Copyright 2017-2020 ProfunKtor
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

import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient

import cats.effect.Resource
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import dev.profunktor.fs2rabbit.resiliency.ResilientStream
import java.util.concurrent.Executors

object ZIOAutoAckConsumer extends CatsApp {

  val config = Fs2RabbitConfig(
    virtualHost = "/",
    host = "127.0.0.1",
    username = Some("guest"),
    password = Some("guest"),
    port = 5672,
    ssl = false,
    connectionTimeout = 3,
    requeueOnNack = false,
    requeueOnReject = false,
    internalQueueSize = Some(500)
  )

  val blockerResource =
    Resource
      .make(Task(Executors.newCachedThreadPool()))(es => Task(es.shutdown()))
      .map(Blocker.liftExecutorService)

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    blockerResource
      .use { blocker =>
        RabbitClient[Task](config, blocker).flatMap { client =>
          ResilientStream
            .runF(new AutoAckConsumerDemo[Task](client).program)
        }
      }
      .orDie
      .as(ExitCode.success)

}
