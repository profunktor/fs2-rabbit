package com.github.gvolpe.fs2rabbit.config

import com.typesafe.config.ConfigFactory
import com.rabbitmq.client.ConnectionFactory

/**
  * The manager that will look for the configuration file application.conf and the required
  * properties, and will create a [[Fs2RabbitConfig]].
  * */
object Fs2RabbitConfigManager {

  private val baseConfPath        = "fs2-rabbit.connection"
  private lazy val configuration  = ConfigFactory.load()
  private lazy val safeConfig     = new SafeConfigReader(configuration)

  /**
    * It tries to get the values from the config file but in case the key is non-existent
    * it will be created with default values.
    *
    * @return a [[Fs2RabbitConfig]]
    * */
  def config: Fs2RabbitConfig = {
    val useSsl = safeConfig.boolean(s"$baseConfPath.ssl").getOrElse(false)
    // ports are from https://github.com/rabbitmq/rabbitmq-java-client/blob/master/src/main/java/com/rabbitmq/client/ConnectionFactory.java
    val defaultPort = if (useSsl) ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT else ConnectionFactory.DEFAULT_AMQP_PORT
    Fs2RabbitConfig(
      host = safeConfig.string(s"$baseConfPath.host").getOrElse("localhost"),
      port = safeConfig.int(s"$baseConfPath.port").getOrElse(defaultPort),
      virtualHost = safeConfig.string(s"$baseConfPath.virtual-host").getOrElse("/"),
      connectionTimeout = safeConfig.int(s"$baseConfPath.connection-timeout").getOrElse(60),
      useSsl = safeConfig.boolean(s"$baseConfPath.ssl").getOrElse(false),
      requeueOnNack = safeConfig.boolean("fs2-rabbit.requeue-on-nack").getOrElse(false),
      username = safeConfig.string(s"$baseConfPath.username"),
      password = safeConfig.string(s"$baseConfPath.password")
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
