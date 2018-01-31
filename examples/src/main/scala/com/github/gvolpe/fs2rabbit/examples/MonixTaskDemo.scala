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

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.StreamLoop
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfigManager
import com.github.gvolpe.fs2rabbit.instances.streameval._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.ExecutionContext

object MonixTaskDemo extends IOApp {

  private val config = new Fs2RabbitConfigManager[Task].config

  implicit val appS: ExecutionContext       = scala.concurrent.ExecutionContext.Implicits.global
  implicit val interpreter: Fs2Rabbit[Task] = new Fs2Rabbit[Task](config)

  override def start(args: List[String]): IO[Unit] =
    StreamLoop.run(() => new GenericDemo[Task].program)

}
