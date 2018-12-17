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
import cats.implicits._
import cats.effect.concurrent.{Deferred, Ref}
import com.github.gvolpe.fs2rabbit.{EffectAssertion, RTS, TestWorld}
import com.github.gvolpe.fs2rabbit.algebra.AMQPInternals
import com.github.gvolpe.fs2rabbit.config.declaration._
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.model.AckResult.{Ack, NAck}
import com.github.gvolpe.fs2rabbit.program.{AckingProgram, ConsumingProgram}
import fs2.concurrent.Queue
import org.scalatest.{FlatSpecLike, Matchers}

import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import java.nio.charset.StandardCharsets.UTF_8

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
      requeueOnNack = false,
      internalQueueSize = None
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
      requeueOnNack = true,
      internalQueueSize = None
    )

  object TestFs2Rabbit {
    def apply(config: Fs2RabbitConfig): (Fs2Rabbit[IO], IO[Unit], TestWorld) = {
      val interpreter = for {
        publishingQ    <- Queue.bounded[IO, Either[Throwable, AmqpEnvelope[Array[Byte]]]](500)
        listenerQ      <- Queue.bounded[IO, PublishReturn](500)
        ackerQ         <- Queue.bounded[IO, AckResult](500)
        queueRef       <- Ref.of[IO, AMQPInternals[IO]](AMQPInternals(None))
        queues         <- Ref.of[IO, Set[QueueName]](Set.empty)
        consumers      <- Ref.of[IO, Set[ConsumerTag]](Set.empty)
        exchanges      <- Ref.of[IO, Set[ExchangeName]](Set.empty)
        binds          <- Ref.of[IO, Map[String, ExchangeName]](Map.empty)
        connectionOpen <- Ref.of[IO, Boolean](false)
        amqpClient = new AmqpClientInMemory(queues,
                                            exchanges,
                                            binds,
                                            queueRef,
                                            consumers,
                                            publishingQ,
                                            listenerQ,
                                            ackerQ,
                                            config)
        conn         = new ConnectionStub(connectionOpen)
        acker        = new AckingProgram[IO](config, amqpClient)
        internalQ    = new LiveInternalQueue[IO](config.internalQueueSize.getOrElse(500))
        consumer     = new ConsumingProgram[IO](amqpClient, internalQ)
        fs2Rabbit    = new Fs2Rabbit[IO](config, conn, amqpClient, acker, consumer)
        testSuiteRTS = RTS.rabbitRTS(queueRef, publishingQ)
      } yield (fs2Rabbit, testSuiteRTS, TestWorld(connectionOpen, queues, exchanges, ackerQ, binds))
      interpreter.unsafeRunSync()
    }
  }

  private val exchangeName = ExchangeName("ex")
  private val queueName    = QueueName("daQ")
  private val routingKey   = RoutingKey("rk")

  // This is just a sanity check on the connectionstub
  it should "create a connection and a open it during use" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._

      val connectionOpenBeforeUse = world.connectionOpen.get
      val connectionOpenDuringUse = createConnectionChannel use { _ =>
        world.connectionOpen.get
      }
      val connectionOpenAfterUse = world.connectionOpen.get

      connectionOpenBeforeUse.map(_ should be(false)) *>
        connectionOpenDuringUse.map(_ should be(true)) *>
        connectionOpenAfterUse.map(_ should be(false))
  }

  it should "create a connection and a queue with default arguments" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _              <- declareQueue(DeclarationQueueConfig.default(queueName))
          _              <- declareExchange(exchangeName, ExchangeType.Topic)
          declaredQueues <- world.queues.get
        } yield (declaredQueues should contain(queueName))
      }
  }

  it should "create a connection and a queue with options enabled" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _              <- declareQueue(DeclarationQueueConfig(queueName, Durable, Exclusive, AutoDelete, Map.empty))
          _              <- declareExchange(exchangeName, ExchangeType.Topic)
          declaredQueues <- world.queues.get
        } yield (declaredQueues should contain(queueName))
      }
  }

  it should "create a connection and a queue (no wait)" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _              <- declareQueueNoWait(DeclarationQueueConfig.default(queueName))
          _              <- declareExchange(exchangeName, ExchangeType.Topic)
          declaredQueues <- world.queues.get
        } yield (declaredQueues should contain(queueName))
      }
  }

  it should "create a connection and a passive queue" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _              <- declareQueuePassive(queueName)
          _              <- declareExchange(exchangeName, ExchangeType.Topic)
          declaredQueues <- world.queues.get
        } yield (declaredQueues should contain(queueName))
      }
  }

  it should "create a connection and an exchange" in EffectAssertion(TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    createConnectionChannel use { implicit channel =>
      for {
        _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        declaredExchanges <- world.exchanges.get
      } yield (declaredExchanges should contain(exchangeName))
    }
  }

  it should "create a connection and an exchange (no wait)" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
          _                 <- declareExchangeNoWait(DeclarationExchangeConfig.default(exchangeName, ExchangeType.Topic))
          declaredExchanges <- world.exchanges.get
        } yield (declaredExchanges should contain(exchangeName))
      }
  }

  it should "create a connection and declare an exchange passively" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
          _                 <- declareExchangePassive(exchangeName)
          _                 <- bindQueue(queueName, exchangeName, RoutingKey("some.routing.key"))
          declaredExchanges <- world.exchanges.get
        } yield (declaredExchanges should contain(exchangeName))
      }
  }

  it should "return an error as the result of 'basicCancel' if consumer doesn't exist" in EffectAssertion(
    TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    createConnectionChannel use { implicit channel =>
      for {
        _      <- declareExchange(exchangeName, ExchangeType.Topic)
        _      <- declareQueue(DeclarationQueueConfig.default(queueName))
        _      <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        result <- basicCancel(ConsumerTag("something-random")).attempt
      } yield {
        result.left.get shouldBe a[java.io.IOException]
      }
    }
  }

  it should "create an acker consumer and verify both envelope and ack result" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        val setup = for {
          _ <- declareExchange(exchangeName, ExchangeType.Topic)
          _ <- declareQueue(DeclarationQueueConfig.default(queueName))
          _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        } yield ()
        setup >> createAckerConsumer(queueName).use {
          case (acker, consumer) =>
            for {
              testQ     <- Queue.bounded[IO, AmqpEnvelope[String]](100)
              publisher <- createPublisher[String](exchangeName, routingKey)
              _         <- publisher("acker-test")
              _         <- consumer.flatMap(testQ.enqueue1)
              ack       = Ack(DeliveryTag(1))
              _         <- acker(ack)
              result    <- testQ.dequeue1
              ackResult <- world.ackerQ.tryDequeue1
            } yield {
              result should be(
                AmqpEnvelope(DeliveryTag(1),
                             "acker-test",
                             AmqpProperties.empty.copy(contentEncoding = Some("UTF-8")),
                             exchangeName,
                             routingKey,
                             false))
              ackResult should be(Some(Ack(DeliveryTag(1))))
            }
        }
      }
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in EffectAssertion(
    TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
      } yield ()
      setup >> createAckerConsumer(queueName).use {
        case (acker, consumer) =>
          for {
            publisher <- createPublisher[String](exchangeName, routingKey)
            _         <- publisher("NAck-test")
            result    <- consumer
            nack      = NAck(DeliveryTag(1))
            _         <- acker(nack)
            ackResult <- world.ackerQ.tryDequeue1
          } yield {
            result should be(
              AmqpEnvelope(DeliveryTag(1),
                           "NAck-test",
                           AmqpProperties(contentEncoding = Some("UTF-8")),
                           exchangeName,
                           routingKey,
                           false))
            ackResult should be(Some(NAck(DeliveryTag(1))))
          }
      }
    }
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      val consumerArgs =
        ConsumerArgs(consumerTag = ConsumerTag("XclusiveConsumer"), noLocal = false, exclusive = true, args = Map.empty)
      createConnectionChannel use { implicit channel =>
        val setup = for {
          _ <- declareExchange(exchangeName, ExchangeType.Topic)
          _ <- declareQueue(DeclarationQueueConfig.default(queueName))
          _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        } yield ()
        setup >> createAutoAckConsumer(queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
          .use { consumer =>
            for {
              publisher <- createPublisher[String](exchangeName, routingKey)
              _         <- publisher("test")
              result    <- consumer
            } yield {
              result should be(
                AmqpEnvelope(DeliveryTag(1),
                             "test",
                             AmqpProperties(contentEncoding = Some("UTF-8")),
                             exchangeName,
                             routingKey,
                             false))
            }
          }
      }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      val consumerArgs =
        ConsumerArgs(consumerTag = ConsumerTag("XclusiveConsumer"), noLocal = false, exclusive = true, args = Map.empty)
      createConnectionChannel use { implicit channel =>
        val setup = for {
          _ <- declareExchange(exchangeName, ExchangeType.Topic)
          _ <- declareQueue(DeclarationQueueConfig.default(queueName))
          _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        } yield ()
        setup >> createAckerConsumer(queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
          .use {
            case (acker, consumer) =>
              for {
                testQ     <- Queue.bounded[IO, AmqpEnvelope[String]](100)
                publisher <- createPublisher[String](exchangeName, routingKey)
                _         <- publisher("test")
                ack       = Ack(DeliveryTag(1))
                _         <- consumer.flatMap(testQ.enqueue1)
                _         <- acker(ack)
                result    <- testQ.dequeue1
                ackResult <- world.ackerQ.tryDequeue1
              } yield {
                result should be(
                  AmqpEnvelope(DeliveryTag(1),
                               "test",
                               AmqpProperties(contentEncoding = Some("UTF-8")),
                               exchangeName,
                               routingKey,
                               false))
                ackResult should be(Some(Ack(DeliveryTag(1))))
              }
          }
      }
  }

  it should "bind a queue with the nowait parameter set to true" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _     <- declareExchange(exchangeName, ExchangeType.Topic)
          _     <- declareQueue(DeclarationQueueConfig.default(queueName))
          _     <- bindQueueNoWait(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
          binds <- world.binds.get
        } yield
          (
            binds.get(routingKey.value) should be(Some(exchangeName))
          )
      }
  }
  it should "try to delete a queue twice (only first time should be okay)" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._

      val QtoDelete = QueueName("deleteMe")
      createConnectionChannel use { implicit channel =>
        for {
          _      <- declareExchange(exchangeName, ExchangeType.Direct)
          _      <- declareQueue(DeclarationQueueConfig.default(queueName))
          _      <- deleteQueue(DeletionQueueConfig.default(QtoDelete))
          _      <- deleteQueueNoWait(DeletionQueueConfig.default(QtoDelete))
          either <- createAutoAckConsumer(QtoDelete).use(IO.pure).attempt
        } yield {
          either shouldBe a[Left[_, _]]
          either.left.get shouldBe a[java.io.IOException]
        }
      }
  }

  it should "delete an exchange successfully" in EffectAssertion(TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    createConnectionChannel use { implicit channel =>
      for {
        _      <- declareExchange(exchangeName, ExchangeType.Direct)
        either <- deleteExchangeNoWait(DeletionExchangeConfig.default(exchangeName)).attempt
      } yield {
        either shouldBe a[Right[_, _]]
      }
    }
  }

  it should "try to delete an exchange twice (only first time should be okay)" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _      <- declareExchange(exchangeName, ExchangeType.Direct)
          _      <- deleteExchange(DeletionExchangeConfig.default(exchangeName))
          either <- deleteExchangeNoWait(DeletionExchangeConfig.default(exchangeName)).attempt
        } yield {
          either shouldBe a[Left[_, _]]
          either.left.get shouldBe a[java.io.IOException]
        }
      }
  }

  it should "try to unbind a queue when there is no binding" in EffectAssertion(TestFs2Rabbit(config)) {
    (interpreter, world) =>
      import interpreter._
      createConnectionChannel use { implicit channel =>
        for {
          _     <- declareExchange(exchangeName, ExchangeType.Direct)
          _     <- declareQueue(DeclarationQueueConfig.default(queueName))
          _     <- unbindQueue(queueName, exchangeName, routingKey)
          binds <- world.binds.get
        } yield
          (
            binds.get(routingKey.value) should be(None)
          )
      }
  }

  it should "unbind a queue" in EffectAssertion(TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    createConnectionChannel use { implicit channel =>
      for {
        _            <- declareExchange(exchangeName, ExchangeType.Direct)
        _            <- declareQueue(DeclarationQueueConfig.default(queueName))
        _            <- bindQueue(queueName, exchangeName, routingKey)
        beforeUnbind <- world.binds.get
        _            <- unbindQueue(queueName, exchangeName, routingKey)
        afterUnbind  <- world.binds.get
      } yield {
        beforeUnbind.get(routingKey.value) should be(Some((exchangeName)))
        afterUnbind.get(routingKey.value) should be(None)
      }
    }
  }

  it should "create a routing publisher, an auto-ack consumer, publish a message and consume it" in EffectAssertion(
    TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
      } yield ()
      setup >> createAutoAckConsumer(queueName).use { consumer =>
        for {
          publisher <- createRoutingPublisher[String](exchangeName)
          msg       = "test"
          _         <- publisher(routingKey)(msg)
          result    <- consumer
        } yield {
          result should be(
            AmqpEnvelope(DeliveryTag(1),
                         "test",
                         AmqpProperties(contentEncoding = Some("UTF-8")),
                         exchangeName,
                         routingKey,
                         false))
        }
      }
    }
  }

  it should "bind an exchange to another exchange" in EffectAssertion(TestFs2Rabbit(config)) { (interpreter, world) =>
    val sourceExchangeName      = ExchangeName("sourceExchange")
    val destinationExchangeName = ExchangeName("destinationExchange")

    import interpreter._
    val consumerArgs =
      ConsumerArgs(consumerTag = ConsumerTag("XclusiveConsumer"), noLocal = false, exclusive = true, args = Map.empty)
    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _ <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, destinationExchangeName, routingKey)
        _ <- bindExchange(destinationExchangeName, sourceExchangeName, routingKey, ExchangeBindingArgs(Map.empty))
      } yield ()
      setup >> createAckerConsumer(queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs)).use {
        case (acker, consumer) =>
          for {
            testQ     <- Queue.bounded[IO, AmqpEnvelope[String]](100)
            ackerQ    <- Queue.bounded[IO, AckResult](100)
            publisher <- createPublisher[String](sourceExchangeName, routingKey)
            _         <- publisher("test")
            _         <- consumer.flatMap(testQ.enqueue1)
            ack       = Ack(DeliveryTag(1))
            _         <- ackerQ.enqueue1(ack) *> acker(ack)
            result    <- testQ.dequeue1
            ackResult <- ackerQ.dequeue1
          } yield {
            result should be(
              AmqpEnvelope(DeliveryTag(1),
                           "test",
                           AmqpProperties(contentEncoding = Some("UTF-8")),
                           sourceExchangeName,
                           routingKey,
                           false))
            ackResult should be(Ack(DeliveryTag(1)))
          }
      }
    }
  }

  it should "unbind an exchange" in EffectAssertion(TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    val sourceExchangeName      = ExchangeName("sourceExchange")
    val destinationExchangeName = ExchangeName("destinationExchange")
    createConnectionChannel use { implicit channel =>
      for {
        _ <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _ <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _ <- bindExchange(destinationExchangeName, sourceExchangeName, routingKey)
        _ <- unbindExchange(destinationExchangeName, sourceExchangeName, routingKey)
      } yield ()
    }
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in EffectAssertion(
    TestFs2Rabbit(nackConfig)) { (interpreter, world) =>
    import interpreter._
    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
      } yield ()
      setup >> createAckerConsumer(queueName).use {
        case (acker, consumer) =>
          for {
            ackerQ    <- Queue.bounded[IO, AckResult](100)
            publisher <- createPublisher[String](exchangeName, routingKey)
            _         <- publisher("NAck-test")
            result    <- consumer
            nack      = NAck(DeliveryTag(1))
            _         <- ackerQ.enqueue1(nack) *> acker(nack)
            _         <- consumer // Message will be re-queued
            ackResult <- ackerQ.dequeue1
          } yield {
            result shouldBe an[AmqpEnvelope[String]]
            ackResult should be(NAck(DeliveryTag(1)))
          }
      }
    }
  }

  it should "create a publisher, two auto-ack consumers with different routing keys and assert the routing is correct" in EffectAssertion(
    TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    val diffQ = QueueName("diffQ")

    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey)
        _ <- declareQueue(DeclarationQueueConfig.default(diffQ))
        _ <- bindQueue(queueName, exchangeName, RoutingKey("diffRK"))
      } yield ()
      setup >> createAutoAckConsumer(queueName).use { c1 =>
        createAutoAckConsumer(diffQ).use { c2 =>
          for {
            publisher <- createPublisher[String](exchangeName, routingKey)
            _         <- publisher("test")
            result    <- c1
            rs2       <- c2.timeoutTo(1.second, IO.pure(None))
          } yield {
            result should be(
              AmqpEnvelope(DeliveryTag(1),
                           "test",
                           AmqpProperties(contentEncoding = Some("UTF-8")),
                           exchangeName,
                           routingKey,
                           false))
            rs2 should be(None)
          }
        }
      }
    }
  }

  def listener(promise: Deferred[IO, PublishReturn]): PublishReturn => IO[Unit] = promise.complete

  it should "create a publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in EffectAssertion(
    TestFs2Rabbit(config)) { (interpreter, world) =>
    import org.scalatest.OptionValues._
    import interpreter._
    val flag = PublishingFlag(mandatory = true)

    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
      } yield ()
      setup >> createAutoAckConsumer(queueName).use { consumer =>
        for {
          promise   <- Deferred[IO, PublishReturn]
          publisher <- createPublisherWithListener(exchangeName, RoutingKey("diff-rk"), flag, listener(promise))
          _         <- publisher("test")
          callback  <- promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))
          result    <- consumer.timeoutTo(500.millis, None.pure[IO])
        } yield {
          result should be(None)
          callback.value.body.value should equal("test".getBytes(UTF_8))
          callback.value.routingKey should be(RoutingKey("diff-rk"))
          callback.value.replyText should be(ReplyText("test"))
        }
      }
    }
  }

  it should "create a routing publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a non-routable message and return to the listener" in EffectAssertion(
    TestFs2Rabbit(config)) { (interpreter, world) =>
    import org.scalatest.OptionValues._
    import interpreter._
    val flag = PublishingFlag(mandatory = true)

    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
      } yield ()
      setup >> createAutoAckConsumer(queueName).use { consumer =>
        for {
          promise   <- Deferred[IO, PublishReturn]
          publisher <- createRoutingPublisherWithListener[String](exchangeName, flag, listener(promise))
          msg       = "test"
          _         <- publisher(RoutingKey("diff-rk"))(msg)
          callback  <- promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))
          result    <- consumer.timeoutTo(500.millis, IO.pure(None))
        } yield {
          result should be(None)
          callback.value.body.value should equal("test".getBytes(UTF_8))
          callback.value.routingKey should be(RoutingKey("diff-rk"))
          callback.value.replyText should be(ReplyText("test"))
        }
      }
    }
  }

  it should "create a routing publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a routable message and not return to the listener" in EffectAssertion(
    TestFs2Rabbit(config)) { (interpreter, world) =>
    import interpreter._
    val flag = PublishingFlag(mandatory = true)

    createConnectionChannel use { implicit channel =>
      val setup = for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
      } yield ()
      setup >> createAutoAckConsumer(queueName).use { consumer =>
        for {
          promise   <- Deferred[IO, PublishReturn]
          publisher <- createRoutingPublisherWithListener[String](exchangeName, flag, listener(promise))
          msg       = "test"
          _         <- publisher(routingKey)(msg)
          callback  <- promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))
          result    <- consumer.timeoutTo(500.millis, IO.pure(None))
        } yield {
          callback should be(None)
          result should be(
            AmqpEnvelope(DeliveryTag(1),
                         "test",
                         AmqpProperties(contentEncoding = Some("UTF-8")),
                         exchangeName,
                         routingKey,
                         false))
        }
      }
    }
  }
}
