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

package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._
import fs2._
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}

// To take into account: https://www.rabbitmq.com/interoperability.html
class Fs2RabbitInterpreterSpec extends FlatSpecLike with Matchers with BeforeAndAfterEach {

  private val config = IO(
    Fs2RabbitConfig("127.0.0.1", 5672, "/", 3, useSsl = false,
      requeueOnNack = false, username = Some("guest"),
      password = Some("guest")))

  private val nackConfig = config.map(_.copy(requeueOnNack = true))

  private val fs2RabbitInterpreter     = new Fs2Rabbit[IO](config)
  private val fs2RabbitNackInterpreter = new Fs2Rabbit[IO](nackConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    import fs2RabbitInterpreter._
//    deleteQueueNoWait()
//    Thread.sleep(1000L)
  }

  case class RabbitMQData(prefix: String) {
    val exchangeName = ExchangeName(s"$prefix-ex")
    val queueName    = QueueName(s"$prefix-daQ")
    val routingKey   = RoutingKey(s"$prefix-rk")
  }

  it should "create a connection, a channel, a queue and an exchange" in StreamAssertion {
    val data = RabbitMQData("one")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        queueD     <- declareQueue(data.queueName)
        _          <- declareExchange(data.exchangeName, ExchangeType.Topic)
        connection = channel.getConnection
      } yield {
        channel.getChannelNumber should be(1)
        queueD.getQueue should be(data.queueName.value)
        connection.getAddress.isLoopbackAddress should be(true)
        connection.toString should startWith("amqp://guest@")
        connection.toString should endWith("127.0.0.1:5672/")
      }
    }
  }

  it should "create an acker consumer and verify both envelope and ack result" in StreamAssertion {
    val data = RabbitMQData("two")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(data.queueName)
        _                 <- bindQueue(data.queueName, data.exchangeName, data.routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
        publisher         <- createPublisher(data.exchangeName, data.routingKey)
        msg               = Stream(AmqpMessage("acker-test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        ackerConsumer     <- createAckerConsumer(data.queueName)
        (acker, consumer) = ackerConsumer
        _ <- Stream(
              consumer to testQ.enqueue,
              Stream(Ack(DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker
            ).join(2).take(1)
        result    <- Stream.eval(testQ.dequeue1)
        ackResult <- Stream.eval(ackerQ.dequeue1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1),
                       "acker-test",
                       AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(Ack(DeliveryTag(1)))
      }
    }
  }

  ignore should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in StreamAssertion {
    val data = RabbitMQData("three")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(data.queueName)
        _                 <- bindQueue(data.queueName, data.exchangeName, data.routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
        publisher         <- createPublisher(data.exchangeName, data.routingKey)
        msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        ackerConsumer     <- createAckerConsumer(data.queueName)
        (acker, consumer) = ackerConsumer
        _                 <- (consumer to testQ.enqueue).take(1)
        _ <- (Stream(NAck(DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker)
              .take(1)
        result    <- Stream.eval(testQ.dequeue1)
        ackResult <- Stream.eval(ackerQ.dequeue1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "NAck-test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(NAck(DeliveryTag(1)))
      }
    }
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in StreamAssertion {
    val data = RabbitMQData("four")

    import fs2RabbitNackInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(data.queueName)
        _                 <- bindQueue(data.queueName, data.exchangeName, data.routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
        publisher         <- createPublisher(data.exchangeName, data.routingKey)
        msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        ackerConsumer     <- createAckerConsumer(data.queueName)
        (acker, consumer) = ackerConsumer
        _                 <- (consumer to testQ.enqueue).take(2) // Message will be requeued
        _ <- (Stream(NAck(DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker)
              .take(1)
        result    <- testQ.dequeue.take(1)
        ackResult <- ackerQ.dequeue.take(1)
      } yield {
        result shouldBe an[AmqpEnvelope]
        ackResult should be(NAck(DeliveryTag(1)))
      }
    }
  }

  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in StreamAssertion {
    val data = RabbitMQData("five")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        _         <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _         <- declareQueue(data.queueName)
        _         <- bindQueue(data.queueName, data.exchangeName, data.routingKey)
        publisher <- createPublisher(data.exchangeName, data.routingKey)
        consumer  <- createAutoAckConsumer(data.queueName)
        msg       = Stream(AmqpMessage("test", AmqpProperties.empty))
        _         <- msg.covary[IO] to publisher
        _         <- consumer.flatMap(env => Stream.eval(testQ.enqueue1(env)))
        result    <- Stream.eval(testQ.dequeue1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      }
    }
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in StreamAssertion {
    val data = RabbitMQData("six")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        _         <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _         <- declareQueue(data.queueName)
        _         <- bindQueue(data.queueName, data.exchangeName, data.routingKey)
        publisher <- createPublisher(data.exchangeName, data.routingKey)
        consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                    noLocal = false,
                                    exclusive = true,
                                    args = Map.empty[String, AnyRef])
        consumer <- createAutoAckConsumer(data.queueName,
                                          BasicQos(prefetchSize = 0, prefetchCount = 10),
                                          Some(consumerArgs))
        msg    = Stream(AmqpMessage("test", AmqpProperties.empty))
        _      <- msg.covary[IO] to publisher
        _      <- (consumer to testQ.enqueue).take(1)
        result <- Stream.eval(testQ.dequeue1)
      } yield {
        println(consumer)
        result should be(
          AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      }
    }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in StreamAssertion {
    val data = RabbitMQData("seven")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ    <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _         <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _         <- declareQueue(data.queueName)
        _         <- bindQueue(data.queueName, data.exchangeName, data.routingKey)
        publisher <- createPublisher(data.exchangeName, data.routingKey)
        consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                    noLocal = false,
                                    exclusive = true,
                                    args = Map.empty[String, AnyRef])
        ackerConsumer <- createAckerConsumer(data.queueName,
                                             BasicQos(prefetchSize = 0, prefetchCount = 10),
                                             Some(consumerArgs))
        (acker, consumer) = ackerConsumer
        msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        _ <- Stream(
              consumer to testQ.enqueue,
              Stream(Ack(DeliveryTag(1))).covary[IO] observe ackerQ.enqueue to acker
            ).join(2).take(1)
        result    <- Stream.eval(testQ.dequeue1)
        ackResult <- Stream.eval(ackerQ.dequeue1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(Ack(DeliveryTag(1)))
      }
    }
  }

  it should "bind a queue with the nowait parameter set to true" in StreamAssertion {
    val data = RabbitMQData("eight")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _ <- declareQueue(data.queueName)
        _ <- bindQueueNoWait(data.queueName, data.exchangeName, data.routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
      } yield ()
    }
  }

  it should "delete a queue" in StreamAssertion {
    val data = RabbitMQData("nine")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _        <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _        <- declareQueue(data.queueName)
        _        <- deleteQueue(data.queueName)
        consumer <- createAutoAckConsumer(data.queueName)
        either   <- consumer.attempt
      } yield {
        either shouldBe a[Left[_, _]]
        either.left.get shouldBe a[java.io.IOException]
      }
    }
  }

  it should "fail to unbind a queue when there is no binding" in StreamAssertion {
    val data = RabbitMQData("ten")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _      <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _      <- declareQueue(data.queueName)
        either <- unbindQueue(data.queueName, data.exchangeName, data.routingKey).attempt
      } yield {
        either shouldBe a[Left[_, _]]
        either.left.get shouldBe a[java.io.IOException]
      }
    }
  }

  it should "unbind a queue" in StreamAssertion {
    val data = RabbitMQData("eleven")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(data.exchangeName, ExchangeType.Topic)
        _ <- declareQueue(data.queueName)
        _ <- bindQueue(data.queueName, data.exchangeName, data.routingKey)
        _ <- unbindQueue(data.queueName, data.exchangeName, data.routingKey)
      } yield ()
    }
  }

  ignore should "bind an exchange to another exchange" in StreamAssertion {
    val data                    = RabbitMQData("twelve")
    val sourceExchangeName      = ExchangeName("sourceExchange")
    val destinationExchangeName = ExchangeName("destinationExchange")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ  <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _      <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _      <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _      <- declareQueue(data.queueName)
        _      <- bindQueue(data.queueName, destinationExchangeName, data.routingKey)
        _ <- bindExchange(destinationExchangeName,
                          sourceExchangeName,
                          data.routingKey,
                          ExchangeBindingArgs(Map.empty[String, AnyRef]))
        publisher <- createPublisher(sourceExchangeName, data.routingKey)
        consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                    noLocal = false,
                                    exclusive = true,
                                    args = Map.empty[String, AnyRef])
        ackerConsumer <- createAckerConsumer(data.queueName,
                                             BasicQos(prefetchSize = 0, prefetchCount = 10),
                                             Some(consumerArgs))
        (acker, consumer) = ackerConsumer
        msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        _ <- Stream(
              consumer to testQ.enqueue,
              Stream(Ack(DeliveryTag(1))).covary[IO] observe ackerQ.enqueue to acker
            ).join(2).take(1)
        result    <- Stream.eval(testQ.dequeue1)
        ackResult <- Stream.eval(ackerQ.dequeue1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(Ack(DeliveryTag(1)))
      }
    }
  }

}
