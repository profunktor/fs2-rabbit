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

package com.github.gvolpe.fs2rabbit.config

import cats.effect.Sync
import cats.syntax.functor._
import com.typesafe.config.ConfigFactory
import com.rabbitmq.client.ConnectionFactory

/**
  * The manager that will look for the configuration file application.conf and the required
  * properties, and will create a [[Fs2RabbitConfig]].
  * */
class Fs2RabbitConfigManager[F[_]](implicit F: Sync[F]) {

  private val baseConfPath        = "fs2-rabbit.connection"

  private val safeConfig: F[SafeConfigReader] = F.delay {
    val configuration = ConfigFactory.load()
    new SafeConfigReader(configuration)
  }

  /**
    * It tries to get the values from the config file but in case the key is non-existent
    * it will be created with default values.
    **/
  def config: F[Fs2RabbitConfig] = safeConfig.map { sc =>
    val useSsl = sc.boolean(s"$baseConfPath.ssl").getOrElse(false)
    // ports are from https://github.com/rabbitmq/rabbitmq-java-client/blob/master/src/main/java/com/rabbitmq/client/ConnectionFactory.java
    val defaultPort = if (useSsl) ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT else ConnectionFactory.DEFAULT_AMQP_PORT
    Fs2RabbitConfig(
      host = sc.string(s"$baseConfPath.host").getOrElse("localhost"),
      port = sc.int(s"$baseConfPath.port").getOrElse(defaultPort),
      virtualHost = sc.string(s"$baseConfPath.virtual-host").getOrElse("/"),
      connectionTimeout = sc.int(s"$baseConfPath.connection-timeout").getOrElse(60),
      useSsl = sc.boolean(s"$baseConfPath.ssl").getOrElse(false),
      requeueOnNack = sc.boolean("fs2-rabbit.requeue-on-nack").getOrElse(false),
      username = sc.string(s"$baseConfPath.username"),
      password = sc.string(s"$baseConfPath.password")
    )
  }

}

case class Fs2RabbitConfig(
  host: String,
  port: Int,
  virtualHost: String,
  connectionTimeout: Int,
  useSsl: Boolean,
  requeueOnNack: Boolean,
  username: Option[String],
  password: Option[String]
)
