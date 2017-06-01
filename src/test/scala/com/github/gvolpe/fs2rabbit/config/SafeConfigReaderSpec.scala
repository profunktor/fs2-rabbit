package com.github.gvolpe.fs2rabbit.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, Matchers}

class SafeConfigReaderSpec extends FlatSpecLike with Matchers {

  it should "read the configuration without throwing exceptions" in {
    val confReader = new SafeConfigReader(ConfigFactory.load("reference.conf"))

    confReader.string("fs2-rabbit.connection.host")   should be (Some("127.0.0.1"))
    confReader.int("fs2-rabbit.connection.port")      should be (Some(5672))
    confReader.boolean("fs2-rabbit.requeue-on-nack")  should be (Some(false))
    confReader.string("fs2-rabbit.nope")              should be (None)
  }

}
