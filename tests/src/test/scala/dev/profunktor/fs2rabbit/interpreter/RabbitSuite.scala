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

package dev.profunktor.fs2rabbit.interpreter

import cats.effect.{ContextShift, IO}
import cats.implicits._
import dev.profunktor.fs2rabbit.BaseSpec
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig

import scala.concurrent.ExecutionContext

class RabbitSuite extends BaseSpec with Fs2RabbitSpec {

  override implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  override val config: Fs2RabbitConfig =
    Fs2RabbitConfig(
      host = "localhost",
      port = 5672,
      virtualHost = "/",
      connectionTimeout = 30,
      ssl = false,
      username = "guest".some,
      password = "guest".some,
      requeueOnNack = false,
      requeueOnReject = false,
      internalQueueSize = 500.some
    )

}
