/*
 * Copyright 2017-2019 Fs2 Rabbit
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

import cats.effect.IO
import cats.effect.concurrent.{Deferred, Ref}
import cats.syntax.option._
import com.github.gvolpe.fs2rabbit.StreamAssertion
import com.github.gvolpe.fs2rabbit.algebra.AMQPInternals
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.config.declaration._
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.model.AckResult.{Ack, NAck}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program.{AckerProgram, ConsumerProgram}
import fs2.Stream
import fs2.concurrent.Queue
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Fs2RabbitSpec extends FlatSpecLike with Matchers {

  implicit val timer = IO.timer(ExecutionContext.global)
  implicit val cs    = IO.contextShift(ExecutionContext.global)

  private val config =
    Fs2RabbitConfig(
      "localhost",
      45947,
      "hostnameAlias",
      3,
      ssl = false,
      username = None,
      password = None,
      requeueOnNack = false
    )

  private val nackConfig =
    Fs2RabbitConfig(
      "localhost",
      45947,
      "hostnameAlias",
      3,
      ssl = false,
      username = None,
      password = None,
      requeueOnNack = true
    )

  /**
    * Runtime Test Suite that makes sure the internal queues are connected when publishing and consuming in order to
    * simulate a running RabbitMQ server. It should run concurrently with every single test.
    * */
  def rabbitRTS(
      ref: Ref[IO, AMQPInternals[IO, String]],
      publishingQ: Queue[IO, Either[Throwable, AmqpEnvelope[String]]]
  ): Stream[IO, Unit] =
    Stream.eval(ref.get).flatMap { internals =>
      internals.queue.fold(rabbitRTS(ref, publishingQ)) { internalQ =>
        for {
          _ <- Stream.eval(publishingQ.dequeue1).to(_.evalMap(internalQ.enqueue1)).take(1)
          _ <- Stream.eval(ref.set(AMQPInternals(None)))
          _ <- rabbitRTS(ref, publishingQ)
        } yield ()
      }
    }

  object TestFs2Rabbit {
    def apply(config: Fs2RabbitConfig): (Fs2Rabbit[IO], Stream[IO, Unit]) = {
      val interpreter = for {
        publishingQ  <- Queue.bounded[IO, Either[Throwable, AmqpEnvelope[String]]](500)
        listenerQ    <- Queue.bounded[IO, PublishReturn](500)
        ackerQ       <- Queue.bounded[IO, AckResult](500)
        queueRef     <- Ref.of[IO, AMQPInternals[IO, String]](AMQPInternals(None))
        queues       <- Ref.of[IO, Set[QueueName]](Set.empty)
        exchanges    <- Ref.of[IO, Set[ExchangeName]](Set.empty)
        binds        <- Ref.of[IO, Map[String, ExchangeName]](Map.empty)
        amqpClient   = new AMQPClientInMemory(queues, exchanges, binds, queueRef, publishingQ, listenerQ, ackerQ, config)
        connStream   = new ConnectionStub
        acker        = new AckerProgram[IO](config, amqpClient)
        consumer     = new ConsumerProgram[IO](amqpClient)
        fs2Rabbit    = new Fs2Rabbit[IO](config, connStream, amqpClient, acker, consumer)
        testSuiteRTS = rabbitRTS(queueRef, publishingQ)
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

  it should "create a connection and a passive queue" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
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

  it should "create a connection and an exchange (no wait)" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- declareExchangeNoWait(DeclarationExchangeConfig.default(exchangeName, ExchangeType.Topic))
      } yield ()
    }
  }

  it should "create a connection and declare an exchange passively" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          _ <- declareQueue(DeclarationQueueConfig.default(queueName))
          _ <- declareExchangePassive(exchangeName)
          _ <- bindQueue(queueName, exchangeName, RoutingKey("some.routing.key"))
        } yield ()
      }
  }

  it should "create an acker consumer and verify both envelope and ack result" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          testQ             <- Stream.eval(Queue.bounded[IO, AmqpEnvelope[String]](100))
          ackerQ            <- Stream.eval(Queue.bounded[IO, AckResult](100))
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
              ).parJoin(2).take(1)
          result    <- Stream.eval(testQ.dequeue1)
          ackResult <- Stream.eval(ackerQ.dequeue1)
        } yield {
          result should be(AmqpEnvelope(DeliveryTag(1), "acker-test", AmqpProperties()))
          ackResult should be(Ack(DeliveryTag(1)))
        }
      }
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in StreamAssertion(
    TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        ackerQ            <- Stream.eval(Queue.bounded[IO, AckResult](100))
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
        result should be(AmqpEnvelope(DeliveryTag(1), "NAck-test", AmqpProperties()))
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
        result should be(AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties()))
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
          result should be(AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties()))
        }
      }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in StreamAssertion(TestFs2Rabbit(config)) {
    interpreter =>
      import interpreter._
      createConnectionChannel flatMap { implicit channel =>
        for {
          testQ     <- Stream.eval(Queue.bounded[IO, AmqpEnvelope[String]](100))
          ackerQ    <- Stream.eval(Queue.bounded[IO, AckResult](100))
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
              ).parJoin(2).take(1)
          result    <- Stream.eval(testQ.dequeue1)
          ackResult <- Stream.eval(ackerQ.dequeue1)
        } yield {
          result should be(AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties()))
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
        testQ     <- Stream.eval(Queue.bounded[IO, AmqpEnvelope[String]](100))
        ackerQ    <- Stream.eval(Queue.bounded[IO, AckResult](100))
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
            ).parJoin(2).take(1)
        result    <- Stream.eval(testQ.dequeue1)
        ackResult <- Stream.eval(ackerQ.dequeue1)
      } yield {
        result should be(AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties()))
        ackResult should be(Ack(DeliveryTag(1)))
      }
    }
  }

  it should "unbind an exchange" in StreamAssertion(TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    val sourceExchangeName      = ExchangeName("sourceExchange")
    val destinationExchangeName = ExchangeName("destinationExchange")
    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _ <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _ <- bindExchange(destinationExchangeName, sourceExchangeName, routingKey)
        _ <- unbindExchange(destinationExchangeName, sourceExchangeName, routingKey)
      } yield ()
    }
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in StreamAssertion(
    TestFs2Rabbit(nackConfig)) { interpreter =>
    import interpreter._
    createConnectionChannel flatMap { implicit channel =>
      for {
        ackerQ            <- Stream.eval(Queue.bounded[IO, AckResult](100))
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
        result shouldBe an[AmqpEnvelope[String]]
        ackResult should be(NAck(DeliveryTag(1)))
      }
    }
  }

  def takeWithTimeOut[A](stream: Stream[IO, A], timeout: FiniteDuration): Stream[IO, Option[A]] =
    stream.last.mergeHaltR(Stream.sleep(timeout).map(_ => none[A]))

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
        result should be(AmqpEnvelope(DeliveryTag(1), "test", AmqpProperties()))
        rs2 should be(None)
      }
    }
  }

  def listener(promise: Deferred[IO, PublishReturn]): PublishingListener[IO] = promise.complete

  it should "create a publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in StreamAssertion(
    TestFs2Rabbit(config)) { interpreter =>
    import interpreter._
    val flag = PublishingFlag(mandatory = true)

    createConnectionChannel.flatMap { implicit channel =>
      for {
        _         <- declareExchange(exchangeName, ExchangeType.Topic)
        _         <- declareQueue(DeclarationQueueConfig.default(queueName))
        _         <- bindQueue(queueName, exchangeName, routingKey)
        promise   <- Stream.eval(Deferred[IO, PublishReturn])
        publisher <- createPublisherWithListener(exchangeName, RoutingKey("diff-rk"), flag, listener(promise))
        consumer  <- createAutoAckConsumer(queueName)
        msg       = Stream(AmqpMessage("test", AmqpProperties.empty))
        _         <- msg.covary[IO] to publisher
        callback  <- Stream.eval(promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))).unNone
        result    <- takeWithTimeOut(consumer, 500.millis)
      } yield {
        result should be(None)
        callback.body should be(AmqpBody("test"))
        callback.routingKey should be(RoutingKey("diff-rk"))
        callback.replyText should be(ReplyText("test"))
      }
    }
  }

}
