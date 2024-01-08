/*
 * Copyright 2017-2024 ProfunKtor
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

import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import com.rabbitmq.client.ConnectionFactory

import scala.concurrent.duration.FiniteDuration

case class Fs2RabbitNodeConfig(
    host: String,
    port: Int
)

case class Fs2RabbitConfig(
    nodes: NonEmptyList[Fs2RabbitNodeConfig],
    virtualHost: String,
    connectionTimeout: FiniteDuration,
    ssl: Boolean,
    username: Option[String],
    password: Option[String],
    requeueOnNack: Boolean,
    requeueOnReject: Boolean,
    internalQueueSize: Option[Int],
    requestedHeartbeat: FiniteDuration,
    automaticRecovery: Boolean,
    clientProvidedConnectionName: Option[String]
)

object Fs2RabbitConfig {
  def apply(
      host: String,
      port: Int,
      virtualHost: String,
      connectionTimeout: FiniteDuration,
      ssl: Boolean,
      username: Option[String],
      password: Option[String],
      requeueOnNack: Boolean,
      requeueOnReject: Boolean,
      internalQueueSize: Option[Int],
      requestedHeartbeat: FiniteDuration = FiniteDuration(ConnectionFactory.DEFAULT_HEARTBEAT, TimeUnit.SECONDS),
      automaticRecovery: Boolean = true,
      clientProvidedConnectionName: Option[String] = None
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
    automaticRecovery = automaticRecovery,
    clientProvidedConnectionName = clientProvidedConnectionName
  )
}
