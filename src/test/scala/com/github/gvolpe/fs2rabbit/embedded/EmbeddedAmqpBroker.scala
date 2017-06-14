package com.github.gvolpe.fs2rabbit.embedded

import org.apache.qpid.server.{Broker, BrokerOptions}

object EmbeddedAmqpBroker {

  private val broker = new Broker()

  def start(): Unit = {
    val config = new BrokerOptions()
    config.setConfigProperty("qpid.amqp_port", "5672")
    config.setConfigProperty("qpid.broker.defaultPreferenceStoreAttributes", "{\"type\": \"Noop\"}")
    config.setConfigProperty("qpid.vhost", "/")
    config.setConfigurationStoreType("Memory")
    config.setStartupLoggedToSystemOut(false)
    broker.startup(config)
  }

  def shutdown(): Unit = {
    broker.shutdown()
  }

}
