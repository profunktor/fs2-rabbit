package com.github.gvolpe.fs2rabbit

import com.github.gvolpe.fs2rabbit.model.{AmqpHeaderVal, AmqpProperties}
import org.scalatest.{FlatSpecLike, Matchers}

class AmqpPropertiesSpec extends FlatSpecLike with Matchers {

  it should "create an empty amqp properties" in {
    AmqpProperties.empty should be (AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal]))
  }

  it should "convert from and to Java AMQP.BasicProperties" in {
    val props = AmqpProperties(Some("application/json"), Some("UTF-8"), Map.empty[String, AmqpHeaderVal])
    val basic = props.asBasicProps
    AmqpProperties.from(basic) should be (props)
  }

}
