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

package com.github.gvolpe.fs2rabbit.interpreter

import cats.effect.{Effect, IO}
import com.github.gvolpe.fs2rabbit.StreamAssertion
import com.github.gvolpe.fs2rabbit.config.{Fs2RabbitConfig, QueueConfig}
import com.github.gvolpe.fs2rabbit.instances.streameval._
import com.github.gvolpe.fs2rabbit.model._
import fs2.{Stream, async}
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class Fs2RabbitSpec extends FlatSpecLike with Matchers {

  private val config = IO(
    Fs2RabbitConfig("localhost",
                    45947,
                    "hostnameAlias",
                    3,
                    useSsl = false,
                    requeueOnNack = false,
                    username = None,
                    password = None))
  private val nackConfig = IO(
    Fs2RabbitConfig("localhost",
                    45947,
                    "hostnameAlias",
                    3,
                    useSsl = false,
                    requeueOnNack = true,
                    username = None,
                    password = None))

  private val daQ    = fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](100).unsafeRunSync()
  private val ackerQ = fs2.async.boundedQueue[IO, AckResult](100).unsafeRunSync()

  object TestFs2Rabbit {
    def apply(config: IO[Fs2RabbitConfig]): Fs2Rabbit[IO] = {
      val interpreter = for {
        internalQ  <- fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](500)
        ackerQ     <- fs2.async.boundedQueue[IO, AckResult](500)
        amqpClient <- IO(new AMQPClientInMemory(internalQ, ackerQ, config))
        connStream <- IO(new ConnectionStub)
        fs2Rabbit  <- IO(new Fs2Rabbit[IO](config, connStream, internalQ)(Effect[IO], amqpClient))
      } yield fs2Rabbit
      interpreter.unsafeRunSync()
    }
  }

  private val fs2RabbitInterpreter     = TestFs2Rabbit(config)
  private val fs2RabbitNackInterpreter = TestFs2Rabbit(nackConfig)

  private val exchangeName = ExchangeName("ex")
  private val queueName    = QueueName("daQ")
  private val routingKey   = RoutingKey("rk")

  it should "create a connection and a queue with default arguments" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(QueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a queue (no wait)" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueueNoWait(QueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a passive" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueuePassive(queueName)
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and an exchange" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(QueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create an acker consumer and verify both envelope and ack result" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(QueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
        publisher         <- createPublisher(exchangeName, routingKey)
        msg               = Stream(AmqpMessage("acker-test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        ackerConsumer     <- createAckerConsumer(queueName)
        (acker, consumer) = ackerConsumer
        _ <- Stream(
              consumer.take(1) to testQ.enqueue,
              Stream(Ack(DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker
            ).join(2).take(1)
        result    <- Stream.eval(testQ.dequeue1)
        ackResult <- Stream.eval(ackerQ.dequeue1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "acker-test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(Ack(DeliveryTag(1)))
      }
    }
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(QueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
        publisher         <- createPublisher(exchangeName, routingKey)
        msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        ackerConsumer     <- createAckerConsumer(queueName)
        (acker, consumer) = ackerConsumer
        result            <- consumer.take(1)
        _                 <- (Stream(NAck(DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker).take(1)
        ackResult         <- Stream.eval(ackerQ.dequeue1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "NAck-test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(NAck(DeliveryTag(1)))
      }
    }
  }

  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _         <- declareExchange(exchangeName, ExchangeType.Topic)
        _         <- declareQueue(QueueConfig.default(queueName))
        _         <- bindQueue(queueName, exchangeName, routingKey)
        publisher <- createPublisher(exchangeName, routingKey)
        consumer  <- createAutoAckConsumer(queueName)
        msg       = Stream(AmqpMessage("test", AmqpProperties.empty))
        _         <- msg.covary[IO] to publisher
        result    <- consumer.take(1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      }
    }
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _         <- declareExchange(exchangeName, ExchangeType.Topic)
        _         <- declareQueue(QueueConfig.default(queueName))
        _         <- bindQueue(queueName, exchangeName, routingKey)
        publisher <- createPublisher(exchangeName, routingKey)
        consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                    noLocal = false,
                                    exclusive = true,
                                    args = Map.empty[String, AnyRef])
        consumer <- createAutoAckConsumer(queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
        msg      = Stream(AmqpMessage("test", AmqpProperties.empty))
        _        <- msg.covary[IO] to publisher
        result   <- consumer.take(1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      }
    }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ    <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _         <- declareExchange(exchangeName, ExchangeType.Topic)
        _         <- declareQueue(QueueConfig.default(queueName))
        _         <- bindQueue(queueName, exchangeName, routingKey)
        publisher <- createPublisher(exchangeName, routingKey)
        consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                    noLocal = false,
                                    exclusive = true,
                                    args = Map.empty[String, AnyRef])
        ackerConsumer <- createAckerConsumer(queueName,
                                             BasicQos(prefetchSize = 0, prefetchCount = 10),
                                             Some(consumerArgs))
        (acker, consumer) = ackerConsumer
        msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        _ <- Stream(
              consumer.take(1) to testQ.enqueue,
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
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(QueueConfig.default(queueName))
        _ <- bindQueueNoWait(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
      } yield ()
    }
  }

  it should "delete a queue" in StreamAssertion {
    import fs2RabbitInterpreter._
    val QtoDelete = QueueName("deleteMe")
    createConnectionChannel flatMap { implicit channel =>
      for {
        _        <- declareExchange(exchangeName, ExchangeType.Direct)
        _        <- declareQueue(QueueConfig.default(queueName))
        _        <- deleteQueue(QtoDelete)
        consumer <- createAutoAckConsumer(QtoDelete)
        either   <- consumer.attempt.take(1)
      } yield {
        either shouldBe a[Left[_, _]]
        either.left.get shouldBe a[java.io.IOException]
      }
    }
  }

  it should "try to unbind a queue when there is no binding" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Direct)
        _ <- declareQueue(QueueConfig.default(queueName))
        _ <- unbindQueue(queueName, exchangeName, routingKey)
      } yield ()
    }
  }

  it should "unbind a queue" in StreamAssertion {
    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Direct)
        _ <- declareQueue(QueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey)
        _ <- unbindQueue(queueName, exchangeName, routingKey)
      } yield ()
    }
  }

  it should "bind an exchange to another exchange" in StreamAssertion {
    val sourceExchangeName      = ExchangeName("sourceExchange")
    val destinationExchangeName = ExchangeName("destinationExchange")

    import fs2RabbitInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ  <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _      <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _      <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _      <- declareQueue(QueueConfig.default(queueName))
        _      <- bindQueue(queueName, destinationExchangeName, routingKey)
        _ <- bindExchange(destinationExchangeName,
                          sourceExchangeName,
                          routingKey,
                          ExchangeBindingArgs(Map.empty[String, AnyRef]))
        publisher <- createPublisher(sourceExchangeName, routingKey)
        consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                    noLocal = false,
                                    exclusive = true,
                                    args = Map.empty[String, AnyRef])
        ackerConsumer <- createAckerConsumer(queueName,
                                             BasicQos(prefetchSize = 0, prefetchCount = 10),
                                             Some(consumerArgs))
        (acker, consumer) = ackerConsumer
        msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        _ <- Stream(
              consumer.take(1) to testQ.enqueue,
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

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in StreamAssertion {
    import fs2RabbitNackInterpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(QueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
        publisher         <- createPublisher(exchangeName, routingKey)
        msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        ackerConsumer     <- createAckerConsumer(queueName)
        (acker, consumer) = ackerConsumer
        result            <- consumer.take(2) // Message will be requeued
        _                 <- (Stream(NAck(DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker).take(1)
        ackResult         <- ackerQ.dequeue.take(1)
      } yield {
        result shouldBe an[AmqpEnvelope]
        ackResult should be(NAck(DeliveryTag(1)))
      }
    }
  }

}
