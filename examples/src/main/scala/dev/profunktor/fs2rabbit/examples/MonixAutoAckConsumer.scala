/*
 * Copyright 2017-2023 ProfunKtor
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

///*
// * Copyright 2017-2020 ProfunKtor
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package dev.profunktor.fs2rabbit.examples
//
//import cats.data.NonEmptyList
//import cats.effect._
//import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
//import dev.profunktor.fs2rabbit.interpreter.RabbitClient
//import dev.profunktor.fs2rabbit.resiliency.ResilientStream
//import monix.eval.{Task, TaskApp}
//import java.util.concurrent.Executors
//
//object MonixAutoAckConsumer extends TaskApp {
//
//  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
//    virtualHost = "/",
//    nodes = NonEmptyList.one(
//      Fs2RabbitNodeConfig(
//        host = "127.0.0.1",
//        port = 5672
//      )
//    ),
//    username = Some("guest"),
//    password = Some("guest"),
//    ssl = false,
//    connectionTimeout = 3,
//    requeueOnNack = false,
//    requeueOnReject = false,
//    internalQueueSize = Some(500),
//    requestedHeartbeat = 60,
//    automaticRecovery = true
//  )
//
//  val blockerResource =
//    Resource
//      .make(Task(Executors.newCachedThreadPool()))(es => Task(es.shutdown()))
//      .map(Blocker.liftExecutorService)
//
//  override def run(args: List[String]): Task[ExitCode] =
//    RabbitClient.resource[Task](config).use { client =>
//      ResilientStream
//        .runF(new AutoAckConsumerDemo[Task](client).program)
//        .as(ExitCode.Success)
//    }
//
//}
