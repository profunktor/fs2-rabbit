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

package com.github.gvolpe.fs2rabbit.examples

import cats.effect.ExitCode
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.resiliency.ResilientStream
import monix.eval.{Task, TaskApp}

object MonixAutoAckConsumer extends TaskApp {

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    host = "127.0.0.1",
    username = Some("guest"),
    password = Some("guest"),
    port = 5672,
    ssl = false,
    sslContext = None,
    connectionTimeout = 3,
    requeueOnNack = false
  )

  implicit val fs2Rabbit: Fs2Rabbit[Task] = Fs2Rabbit[Task](config)

  override def run(args: List[String]): Task[ExitCode] =
    ResilientStream
      .run(new AutoAckConsumerDemo[Task].program)
      .as(ExitCode.Success)

}
