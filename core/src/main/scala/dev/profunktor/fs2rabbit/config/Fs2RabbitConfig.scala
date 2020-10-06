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

package dev.profunktor.fs2rabbit.config

import cats.data.NonEmptyList

case class Fs2RabbitNodeConfig(
    host: String,
    port: Int
)

case class Fs2RabbitConfig(
    nodes: NonEmptyList[Fs2RabbitNodeConfig],
    virtualHost: String,
    connectionTimeout: Int,
    ssl: Boolean,
    username: Option[String],
    password: Option[String],
    requeueOnNack: Boolean,
    requeueOnReject: Boolean,
    internalQueueSize: Option[Int],
    requestedHeartbeat: Option[Int],
    automaticRecovery: Boolean
)

object Fs2RabbitConfig {
  def apply(
      host: String,
      port: Int,
      virtualHost: String,
      connectionTimeout: Int,
      ssl: Boolean,
      username: Option[String],
      password: Option[String],
      requeueOnNack: Boolean,
      requeueOnReject: Boolean,
      internalQueueSize: Option[Int],
      requestedHeartbeat: Option[Int] = None,
      automaticRecovery: Boolean = true
  ): Fs2RabbitConfig = Fs2RabbitConfig(
    nodes = NonEmptyList.one(Fs2RabbitNodeConfig(host, port)),
    virtualHost = virtualHost,
    connectionTimeout = connectionTimeout,
    ssl = ssl,
    username = username,
    password = password,
    requeueOnNack = requeueOnNack,
    requeueOnReject = requeueOnReject,
    internalQueueSize = internalQueueSize,
    requestedHeartbeat = requestedHeartbeat,
    automaticRecovery = automaticRecovery
  )
}
