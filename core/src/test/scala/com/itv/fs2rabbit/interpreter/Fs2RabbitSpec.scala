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

package com.itv.fs2rabbit.interpreter

import cats.effect.IO
import com.itv.fs2rabbit.StreamAssertion
import com.itv.fs2rabbit.algebra.AMQPInternals
import com.itv.fs2rabbit.config.Fs2RabbitConfig
import com.itv.fs2rabbit.config.declaration.{AutoDelete, DeclarationQueueConfig, Durable, Exclusive}
import com.itv.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.itv.fs2rabbit.model.AckResult.{Ack, NAck}
import com.itv.fs2rabbit.model._
import com.itv.fs2rabbit.program.AckerConsumerProgram
import fs2.async.{Ref, mutable}
import fs2.{Stream, async}
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Fs2RabbitSpec extends FlatSpecLike with Matchers {

  private val config =
    Fs2RabbitConfig("localhost",
                    45947,
                    "hostnameAlias",
                    3,
                    ssl = false,
                    sslContext = None,
                    username = None,
                    password = None,
                    requeueOnNack = false)

  private val nackConfig =
    Fs2RabbitConfig("localhost",
                    45947,
                    "hostnameAlias",
                    3,
                    ssl = false,
                    sslContext = None,
                    username = None,
                    password = None,
                    requeueOnNack = true)

  /**
    * Runtime Test Suite that makes sure the internal queues are connected when publishing and consuming in order to
    * simulate a running RabbitMQ server. It should run concurrently with every single test.
    * */
  def rabbitRTS(ref: Ref[IO, AMQPInternals[IO]],
                publishingQ: mutable.Queue[IO, Either[Throwable, AmqpEnvelope]]): Stream[IO, Unit] =
    Stream.eval(ref.get).flatMap { internals =>
      internals.queue.fold(rabbitRTS(ref, publishingQ)) { internalQ =>
        for {
          _ <- (Stream.eval(publishingQ.dequeue1) to (_.evalMap(internalQ.enqueue1))).take(1)
          _ <- Stream.eval(ref.setAsync(AMQPInternals(None)))
          _ <- rabbitRTS(ref, publishingQ)
        } yield ()
      }
    }

  object TestFs2Rabbit {
    def apply(config: Fs2RabbitConfig): (Fs2Rabbit[IO], Stream[IO, Unit]) = {
      val interpreter = for {
        publishingQ   <- fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](500)
        ackerQ        <- fs2.async.boundedQueue[IO, AckResult](500)
        queueRef      <- fs2.async.refOf[IO, AMQPInternals[IO]](AMQPInternals(None))
        amqpClient    = new AMQPClientInMemory(queueRef, publishingQ, ackerQ, config)
        connStream    = new ConnectionStub
        ackerConsumer = new AckerConsumerProgram[IO](config, amqpClient)
        fs2Rabbit     = new Fs2Rabbit[IO](config, connStream, amqpClient, ackerConsumer)
        testSuiteRTS  = rabbitRTS(queueRef, publishingQ)
      } yield (fs2Rabbit, testSuiteRTS)
      interpreter.unsafeRunSync()
    }
  }

  private val exchangeName = ExchangeName("ex")
  private val queueName    = QueueName("daQ")
  private val routingKey   = RoutingKey("rk")

  it should "create a connection and a queue with default arguments" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          _ <- declareQueue(DeclarationQueueConfig.default(queueName))
          _ <- declareExchange(exchangeName, ExchangeType.Topic)
        } yield ()
      }
  }

  it should "create a connection and a queue with options enabled" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          _ <- declareQueue(DeclarationQueueConfig(queueName, Durable, Exclusive, AutoDelete, Map.empty))
          _ <- declareExchange(exchangeName, ExchangeType.Topic)
        } yield ()
      }
  }

  it should "create a connection and a queue (no wait)" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueueNoWait(DeclarationQueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a passive" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueuePassive(queueName)
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and an exchange" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create an acker consumer and verify both envelope and ack result" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
          ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
          _                 <- declareExchange(exchangeName, ExchangeType.Topic)
          _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
          _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
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
            AmqpEnvelope(DeliveryTag(1),
                         "acker-test",
                         AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])))
          ackResult should be(Ack(DeliveryTag(1)))
        }
      }
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in StreamAssertion(
    TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
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
          AmqpEnvelope(DeliveryTag(1),
                       "NAck-test",
                       AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(NAck(DeliveryTag(1)))
      }
    }
  }

  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in StreamAssertion(
    TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _         <- declareExchange(exchangeName, ExchangeType.Topic)
        _         <- declareQueue(DeclarationQueueConfig.default(queueName))
        _         <- bindQueue(queueName, exchangeName, routingKey)
        publisher <- createPublisher(exchangeName, routingKey)
        consumer  <- createAutoAckConsumer(queueName)
        msg       = Stream(AmqpMessage("test", AmqpProperties.empty))
        _         <- msg.covary[IO] to publisher
        result    <- consumer.take(1)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1),
                       "test",
                       AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])))
      }
    }
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          _         <- declareExchange(exchangeName, ExchangeType.Topic)
          _         <- declareQueue(DeclarationQueueConfig.default(queueName))
          _         <- bindQueue(queueName, exchangeName, routingKey)
          publisher <- createPublisher(exchangeName, routingKey)
          consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                      noLocal = false,
                                      exclusive = true,
                                      args = Map.empty)
          consumer <- createAutoAckConsumer(queueName,
                                            BasicQos(prefetchSize = 0, prefetchCount = 10),
                                            Some(consumerArgs))
          msg    = Stream(AmqpMessage("test", AmqpProperties.empty))
          _      <- msg.covary[IO] to publisher
          result <- consumer.take(1)
        } yield {
          result should be(
            AmqpEnvelope(DeliveryTag(1),
                         "test",
                         AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])))
        }
      }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
          ackerQ    <- Stream.eval(async.boundedQueue[IO, AckResult](100))
          _         <- declareExchange(exchangeName, ExchangeType.Topic)
          _         <- declareQueue(DeclarationQueueConfig.default(queueName))
          _         <- bindQueue(queueName, exchangeName, routingKey)
          publisher <- createPublisher(exchangeName, routingKey)
          consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                      noLocal = false,
                                      exclusive = true,
                                      args = Map.empty)
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
            AmqpEnvelope(DeliveryTag(1),
                         "test",
                         AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])))
          ackResult should be(Ack(DeliveryTag(1)))
        }
      }
  }

  it should "bind a queue with the nowait parameter set to true" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          _ <- declareExchange(exchangeName, ExchangeType.Topic)
          _ <- declareQueue(DeclarationQueueConfig.default(queueName))
          _ <- bindQueueNoWait(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        } yield ()
      }
  }

  it should "try to delete a queue twice (only first time should be okay)" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      val QtoDelete = QueueName("deleteMe")
      createConnectionChannel flatMap { implicit channel =>
        for {
          _        <- declareExchange(exchangeName, ExchangeType.Direct)
          _        <- declareQueue(DeclarationQueueConfig.default(queueName))
          _        <- deleteQueue(DeletionQueueConfig.default(QtoDelete))
          _        <- deleteQueueNoWait(DeletionQueueConfig.default(QtoDelete))
          consumer <- createAutoAckConsumer(QtoDelete)
          either   <- consumer.attempt.take(1)
        } yield {
          either shouldBe a[Left[_, _]]
          either.left.get shouldBe a[java.io.IOException]
        }
      }
  }

  it should "delete an exchange successfully" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _      <- declareExchange(exchangeName, ExchangeType.Direct)
        either <- deleteExchangeNoWait(DeletionExchangeConfig.default(exchangeName)).attempt.take(1)
      } yield {
        either shouldBe a[Right[_, _]]
      }
    }
  }

  it should "try to delete an exchange twice (only first time should be okay)" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          _      <- declareExchange(exchangeName, ExchangeType.Direct)
          _      <- deleteExchange(DeletionExchangeConfig.default(exchangeName))
          either <- deleteExchangeNoWait(DeletionExchangeConfig.default(exchangeName)).attempt.take(1)
        } yield {
          either shouldBe a[Left[_, _]]
          either.left.get shouldBe a[java.io.IOException]
        }
      }
  }

  it should "try to unbind a queue when there is no binding" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Direct)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- unbindQueue(queueName, exchangeName, routingKey)
      } yield ()
    }
  }

  it should "unbind a queue" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Direct)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey)
        _ <- unbindQueue(queueName, exchangeName, routingKey)
      } yield ()
    }
  }

  it should "bind an exchange to another exchange" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    val sourceExchangeName      = ExchangeName("sourceExchange")
    val destinationExchangeName = ExchangeName("destinationExchange")

    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
        ackerQ    <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _         <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _         <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _         <- declareQueue(DeclarationQueueConfig.default(queueName))
        _         <- bindQueue(queueName, destinationExchangeName, routingKey)
        _         <- bindExchange(destinationExchangeName, sourceExchangeName, routingKey, ExchangeBindingArgs(Map.empty))
        publisher <- createPublisher(sourceExchangeName, routingKey)
        consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
                                    noLocal = false,
                                    exclusive = true,
                                    args = Map.empty)
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
          AmqpEnvelope(DeliveryTag(1),
                       "test",
                       AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])))
        ackResult should be(Ack(DeliveryTag(1)))
      }
    }
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in StreamAssertion(
    TestFs2Rabbit(nackConfig)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        publisher         <- createPublisher(exchangeName, routingKey)
        msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
        _                 <- msg.covary[IO] to publisher
        ackerConsumer     <- createAckerConsumer(queueName)
        (acker, consumer) = ackerConsumer
        result            <- consumer.take(1)
        _                 <- (Stream(NAck(DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker).take(1)
        _                 <- consumer.take(1) // Message will be re-queued
        ackResult         <- ackerQ.dequeue.take(1)
      } yield {
        result shouldBe an[AmqpEnvelope]
        ackResult should be(NAck(DeliveryTag(1)))
      }
    }
  }

  def takeWithTimeOut[A](stream: Stream[IO, A], timeout: FiniteDuration): Stream[IO, Option[A]] =
    stream.last.mergeHaltR(Stream.eval(IO.sleep(timeout)).map(_ => None: Option[A]))

  it should "create a publisher, two auto-ack consumers with different routing keys and assert the routing is correct" in StreamAssertion(
    TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      val diffQ = QueueName("diffQ")
      for {
        _         <- declareExchange(exchangeName, ExchangeType.Topic)
        publisher <- createPublisher(exchangeName, routingKey)
        _         <- declareQueue(DeclarationQueueConfig.default(queueName))
        _         <- bindQueue(queueName, exchangeName, routingKey)
        _         <- declareQueue(DeclarationQueueConfig.default(diffQ))
        _         <- bindQueue(queueName, exchangeName, RoutingKey("diffRK"))
        c1        <- createAutoAckConsumer(queueName)
        c2        <- createAutoAckConsumer(diffQ)
        msg       = Stream(AmqpMessage("test", AmqpProperties.empty))
        _         <- msg.covary[IO] to publisher
        result    <- c1.take(1)
        rs2       <- takeWithTimeOut(c2, 1.second)
      } yield {
        result should be(
          AmqpEnvelope(DeliveryTag(1),
                       "test",
                       AmqpProperties(None, None, None, None, Map.empty[String, AmqpHeaderVal])))
        rs2 should be(None)
      }
    }
  }

}
