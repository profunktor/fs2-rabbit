package com.github.gvolpe.fs2rabbit.config

import org.scalatest.{FlatSpecLike, Matchers}

class Fs2RabbitConfigManagerSpec extends FlatSpecLike with Matchers {

  it should "read the configuration giving default values if not found" in {
    val config = Fs2RabbitConfigManager.config
    config.connectionTimeout  should be (3)
    config.host               should be ("127.0.0.1")
    config.port               should be (5672)
    config.requeueOnNack      should be (false)
  }

}
