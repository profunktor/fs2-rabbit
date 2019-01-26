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

import cats.effect.concurrent.Deferred
import cats.effect.{IO, Timer}
import cats.syntax.option._
import com.github.gvolpe.fs2rabbit.config.declaration._
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.model.AckResult.{Ack, NAck}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.{DockerRabbit, IOAssertion, model}
import fs2.Stream
import org.scalatest.{EitherValues, FlatSpec, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class Fs2RabbitITSpec extends FlatSpec with Matchers with DockerRabbit with EitherValues {

  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  it should "create a connection and a queue with default arguments" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a queue with options enabled" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(DeclarationQueueConfig(queueName, Durable, Exclusive, AutoDelete, Map.empty))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a queue (no wait)" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueueNoWait(DeclarationQueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a passive queue" in withRabbit { interpreter =>
    import interpreter._

    val queueName = QueueName(randomString())

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- declareQueuePassive(queueName)
      } yield ()
    }
  }

  it should "return an error during creation of a passive queue if queue doesn't exist" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel flatMap { implicit channel =>
      for {
        result <- declareQueuePassive(QueueName(randomString())).attempt
      } yield {
        result.left.value shouldBe a[java.io.IOException]
      }
    }
  }

  it should "return an error during creation of a passive exchange if exchange doesn't exist" in withRabbit {
    interpreter =>
      import interpreter._

      createConnectionChannel flatMap { implicit channel =>
        for {
          result <- declareExchangePassive(ExchangeName(randomString())).attempt
        } yield {
          result.left.value shouldBe a[java.io.IOException]
        }
      }
  }

  it should "create a connection and an exchange" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and an exchange (no wait)" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- declareExchangeNoWait(DeclarationExchangeConfig.default(exchangeName, ExchangeType.Topic))
      } yield ()
    }
  }

  it should "create a connection and declare an exchange passively" in withRabbit { interpreter =>
    import interpreter._

    val (_, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareExchangePassive(exchangeName)
      } yield ()
    }
  }

  it should "return an error as the result of 'basicCancel' if consumer doesn't exist" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _      <- declareExchange(exchangeName, ExchangeType.Topic)
        _      <- declareQueue(DeclarationQueueConfig.default(queueName))
        _      <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        result <- Stream.eval(basicCancel(ConsumerTag(randomString()))).attempt.take(1)
      } yield {
        result.left.value shouldBe a[java.io.IOException]
      }
    }
  }

  it should "create an acker consumer and verify both envelope and ack result" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        publisher         <- createPublisher[String](exchangeName, routingKey)
        _                 <- Stream.eval(publisher("acker-test"))
        ackerConsumer     <- createAckerConsumer(queueName)
        (acker, consumer) = ackerConsumer
        result            <- consumer.take(1)
        _                 <- Stream.eval(acker(Ack(result.deliveryTag)))
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, exchangeName, routingKey, "acker-test")
      }
    }
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        publisher         <- createPublisher[String](exchangeName, routingKey)
        _                 <- Stream.eval(publisher("NAck-test"))
        ackerConsumer     <- createAckerConsumer(queueName)
        (acker, consumer) = ackerConsumer
        result            <- consumer.take(1)
        _                 <- Stream.eval(acker(NAck(result.deliveryTag)))
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, exchangeName, routingKey, "NAck-test")
      }
    }
  }

  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _         <- declareExchange(exchangeName, ExchangeType.Topic)
        _         <- declareQueue(DeclarationQueueConfig.default(queueName))
        _         <- bindQueue(queueName, exchangeName, routingKey)
        publisher <- createPublisher[String](exchangeName, routingKey)
        consumer  <- createAutoAckConsumer(queueName)
        _         <- Stream.eval(publisher("test"))
        result    <- consumer.take(1)
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, exchangeName, routingKey, "test")
      }
    }
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()
    val consumerTag                           = ConsumerTag(randomString())

    createConnectionChannel flatMap { implicit channel =>
      for {
        _            <- declareExchange(exchangeName, ExchangeType.Topic)
        _            <- declareQueue(DeclarationQueueConfig.default(queueName))
        _            <- bindQueue(queueName, exchangeName, routingKey)
        publisher    <- createPublisher[String](exchangeName, routingKey)
        consumerArgs = ConsumerArgs(consumerTag = consumerTag, noLocal = false, exclusive = true, args = Map.empty)
        consumer     <- createAutoAckConsumer(queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
        _            <- Stream.eval(publisher("test"))
        result       <- consumer.take(1)
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, exchangeName, routingKey, "test")
      }
    }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()
    val consumerTag                           = ConsumerTag(randomString())

    createConnectionChannel flatMap { implicit channel =>
      for {
        _            <- declareExchange(exchangeName, ExchangeType.Topic)
        _            <- declareQueue(DeclarationQueueConfig.default(queueName))
        _            <- bindQueue(queueName, exchangeName, routingKey)
        publisher    <- createPublisher[String](exchangeName, routingKey)
        consumerArgs = ConsumerArgs(consumerTag = consumerTag, noLocal = false, exclusive = true, args = Map.empty)
        ackerConsumer <- createAckerConsumer(queueName,
                                             BasicQos(prefetchSize = 0, prefetchCount = 10),
                                             Some(consumerArgs))
        (acker, consumer) = ackerConsumer
        _                 <- Stream.eval(publisher("test"))
        result            <- consumer.take(1)
        _                 <- Stream.eval(acker(Ack(result.deliveryTag)))
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, exchangeName, routingKey, "test")
      }
    }
  }

  it should "bind a queue with the nowait parameter set to true" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Topic)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueueNoWait(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
      } yield ()
    }
  }

  it should "try to delete a queue twice (only first time should be okay)" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _        <- declareExchange(exchangeName, ExchangeType.Direct)
        _        <- declareQueue(DeclarationQueueConfig.default(queueName))
        _        <- deleteQueue(DeletionQueueConfig.default(queueName))
        _        <- deleteQueueNoWait(DeletionQueueConfig.default(queueName))
        consumer <- createAutoAckConsumer(queueName)
        either   <- consumer.attempt.take(1)
      } yield {
        either shouldBe a[Left[_, _]]
        either.left.value shouldBe a[java.io.IOException]
      }
    }
  }

  it should "delete an exchange successfully" in withRabbit { interpreter =>
    import interpreter._

    val (_, exchangeName, _) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _      <- declareExchange(exchangeName, ExchangeType.Direct)
        either <- deleteExchangeNoWait(DeletionExchangeConfig.default(exchangeName)).attempt.take(1)
      } yield {
        either shouldBe a[Right[_, _]]
      }
    }
  }

  it should "try to unbind a queue when there is no binding" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Direct)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- unbindQueue(queueName, exchangeName, routingKey)
      } yield ()
    }
  }

  it should "unbind a queue" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(exchangeName, ExchangeType.Direct)
        _ <- declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- bindQueue(queueName, exchangeName, routingKey)
        _ <- unbindQueue(queueName, exchangeName, routingKey)
      } yield ()
    }
  }

  it should "bind an exchange to another exchange" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, _, routingKey) = randomQueueData()
    val sourceExchangeName         = ExchangeName(randomString())
    val destinationExchangeName    = ExchangeName(randomString())
    val consumerTag                = ConsumerTag(randomString())

    createConnectionChannel flatMap { implicit channel =>
      for {
        _            <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _            <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _            <- declareQueue(DeclarationQueueConfig.default(queueName))
        _            <- bindQueue(queueName, destinationExchangeName, routingKey)
        _            <- bindExchange(destinationExchangeName, sourceExchangeName, routingKey, ExchangeBindingArgs(Map.empty))
        publisher    <- createPublisher[String](sourceExchangeName, routingKey)
        consumerArgs = ConsumerArgs(consumerTag = consumerTag, noLocal = false, exclusive = true, args = Map.empty)
        ackerConsumer <- createAckerConsumer(queueName,
                                             BasicQos(prefetchSize = 0, prefetchCount = 10),
                                             Some(consumerArgs))
        (acker, consumer) = ackerConsumer
        _                 <- Stream.eval(publisher("test"))
        receivedMessage   <- consumer.take(1)
        _                 <- Stream.eval(acker(Ack(receivedMessage.deliveryTag)))
      } yield {
        receivedMessage shouldBe expectedDelivery(receivedMessage.deliveryTag, sourceExchangeName, routingKey, "test")
      }
    }
  }

  it should "unbind an exchange" in withRabbit { interpreter =>
    import interpreter._

    val (_, _, routingKey)      = randomQueueData()
    val sourceExchangeName      = ExchangeName(randomString())
    val destinationExchangeName = ExchangeName(randomString())

    createConnectionChannel flatMap { implicit channel =>
      for {
        _ <- declareExchange(sourceExchangeName, ExchangeType.Direct)
        _ <- declareExchange(destinationExchangeName, ExchangeType.Direct)
        _ <- bindExchange(destinationExchangeName, sourceExchangeName, routingKey)
        _ <- unbindExchange(destinationExchangeName, sourceExchangeName, routingKey)
      } yield ()
    }
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in withNackRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    createConnectionChannel flatMap { implicit channel =>
      for {
        _                 <- declareExchange(exchangeName, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(queueName))
        _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty))
        publisher         <- createPublisher[String](exchangeName, routingKey)
        _                 <- Stream.eval(publisher("NAck-test"))
        ackerConsumer     <- createAckerConsumer(queueName)
        (acker, consumer) = ackerConsumer
        result            <- Stream.eval(consumer.take(1).compile.lastOrError)
        _                 <- Stream.eval(acker(NAck(result.deliveryTag)))
        result2           <- createAutoAckConsumer(queueName).flatten.take(1) // Message will be re-queued
      } yield {
        val expected = expectedDelivery(result.deliveryTag, exchangeName, routingKey, "NAck-test")

        result shouldBe expected
        result2 shouldBe expected.copy(deliveryTag = result2.deliveryTag, redelivered = true)
      }
    }
  }

  it should "create a publisher, two auto-ack consumers with different routing keys and assert the routing is correct" in withRabbit {
    interpreter =>
      import interpreter._

      val (queueName, exchangeName, routingKey) = randomQueueData()
      val diffQ                                 = QueueName(randomString())

      createConnectionChannel flatMap { implicit channel =>
        for {
          _         <- declareExchange(exchangeName, ExchangeType.Topic)
          publisher <- createPublisher[String](exchangeName, routingKey)
          _         <- declareQueue(DeclarationQueueConfig.default(queueName))
          _         <- bindQueue(queueName, exchangeName, routingKey)
          _         <- declareQueue(DeclarationQueueConfig.default(diffQ))
          _         <- bindQueue(queueName, exchangeName, RoutingKey("diffRK"))
          c1        <- createAutoAckConsumer(queueName)
          c2        <- createAutoAckConsumer(diffQ)
          _         <- Stream.eval(publisher("test"))
          result    <- c1.take(1)
          rs2       <- takeWithTimeOut(c2, 1.second)
        } yield {
          result shouldBe expectedDelivery(result.deliveryTag, exchangeName, routingKey, "test")
          rs2 shouldBe None
        }
      }
  }

  it should "create a publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in withRabbit {
    interpreter =>
      import interpreter._

      val (queueName, exchangeName, routingKey) = randomQueueData()
      val flag                                  = PublishingFlag(mandatory = true)

      createConnectionChannel flatMap { implicit channel =>
        for {
          _         <- declareExchange(exchangeName, ExchangeType.Topic)
          _         <- declareQueue(DeclarationQueueConfig.default(queueName))
          _         <- bindQueue(queueName, exchangeName, routingKey)
          promise   <- Stream.eval(Deferred[IO, PublishReturn])
          publisher <- createPublisherWithListener(exchangeName, RoutingKey("diff-rk"), flag, listener(promise))
          consumer  <- createAutoAckConsumer(queueName)
          _         <- Stream.eval(publisher("test"))
          callback  <- Stream.eval(promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))).unNone
          result    <- takeWithTimeOut(consumer, 500.millis)
        } yield {
          result shouldBe None
          callback.body.value shouldEqual "test".getBytes("UTF-8")
          callback.routingKey shouldBe RoutingKey("diff-rk")
        }
      }
  }

  it should "create a routing publisher, an auto-ack consumer, publish a message and consume it" in withRabbit {
    interpreter =>
      import interpreter._

      val (queueName, exchangeName, routingKey) = randomQueueData()

      createConnectionChannel flatMap { implicit channel =>
        for {
          _         <- declareExchange(exchangeName, ExchangeType.Topic)
          _         <- declareQueue(DeclarationQueueConfig.default(queueName))
          _         <- bindQueue(queueName, exchangeName, routingKey)
          publisher <- createRoutingPublisher[String](exchangeName)
          consumer  <- createAutoAckConsumer(queueName)
          _         <- Stream.eval(publisher(routingKey).apply("test"))
          result    <- consumer.take(1)
        } yield {
          result shouldBe expectedDelivery(result.deliveryTag, exchangeName, routingKey, "test")
        }
      }
  }

  it should "create a routing publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in withRabbit {
    interpreter =>
      import interpreter._

      val (queueName, exchangeName, routingKey) = randomQueueData()
      val flag                                  = PublishingFlag(mandatory = true)

      createConnectionChannel flatMap { implicit channel =>
        for {
          _         <- declareExchange(exchangeName, ExchangeType.Topic)
          _         <- declareQueue(DeclarationQueueConfig.default(queueName))
          _         <- bindQueue(queueName, exchangeName, routingKey)
          promise   <- Stream.eval(Deferred[IO, PublishReturn])
          publisher <- createRoutingPublisherWithListener[String](exchangeName, flag, listener(promise))
          consumer  <- createAutoAckConsumer(queueName)
          _         <- Stream.eval(publisher(RoutingKey("diff-rk"))("test"))
          callback  <- Stream.eval(promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))).unNone
          result    <- takeWithTimeOut(consumer, 500.millis)
        } yield {
          result shouldBe None
          callback.body.value shouldEqual "test".getBytes("UTF-8")
          callback.routingKey shouldBe RoutingKey("diff-rk")
        }
      }
  }

  it should "consume published message" in withRabbit { interpreter =>
    import interpreter._

    val (queueName, exchangeName, routingKey) = randomQueueData()

    val message = "Test text here"

    val consumer: Stream[IO, model.AmqpEnvelope[String]] =
      createConnectionChannel flatMap { implicit channel =>
        for {
          _        <- declareQueue(DeclarationQueueConfig.default(queueName))
          _        <- declareExchange(exchangeName, ExchangeType.Topic)
          _        <- bindQueue(queueName, exchangeName, routingKey)
          consumer <- createAutoAckConsumer[String](queueName)
          message  <- consumer
        } yield message
      }

    val producer: Stream[IO, Unit] =
      createConnectionChannel flatMap { implicit channel =>
        for {
          _         <- declareQueue(DeclarationQueueConfig.default(queueName))
          _         <- declareExchange(exchangeName, ExchangeType.Topic)
          _         <- bindQueue(queueName, exchangeName, routingKey)
          publisher <- createPublisher[String](exchangeName, routingKey)
          _         <- Stream.eval(publisher(message))
        } yield ()
      }

    for {
      _               <- producer
      receivedMessage <- consumer.take(1)
    } yield {
      receivedMessage shouldBe expectedDelivery(receivedMessage.deliveryTag, exchangeName, routingKey, message)
    }
  }

  private def withRabbit[A](fa: Fs2Rabbit[IO] => Stream[IO, A]): Unit = IOAssertion {
    Fs2Rabbit[IO](rabbitConfig).flatMap(r => fa(r).compile.drain)
  }

  private def withNackRabbit[A](fa: Fs2Rabbit[IO] => Stream[IO, A]): Unit = IOAssertion {
    Fs2Rabbit[IO](rabbitConfig.copy(requeueOnNack = true)).flatMap(r => fa(r).compile.drain)
  }

  private def randomQueueData(): (QueueName, ExchangeName, RoutingKey) = {
    val queueName    = QueueName(randomString())
    val exchangeName = ExchangeName(randomString())
    val routingKey   = RoutingKey(randomString())

    (queueName, exchangeName, routingKey)
  }

  private def randomString(): String = Random.alphanumeric.take(6).mkString.toLowerCase

  private def expectedDelivery(tag: DeliveryTag, exchangeName: ExchangeName, routingKey: RoutingKey, payload: String) =
    AmqpEnvelope(
      deliveryTag = tag,
      payload = payload,
      properties = AmqpProperties(contentEncoding = Some("UTF-8")),
      exchangeName = exchangeName,
      routingKey = routingKey,
      redelivered = false
    )

  private def takeWithTimeOut[A](stream: Stream[IO, A], timeout: FiniteDuration): Stream[IO, Option[A]] =
    stream.last.mergeHaltR(Stream.sleep[IO](timeout).map(_ => None))

  private def listener(promise: Deferred[IO, PublishReturn]): PublishReturn => IO[Unit] = promise.complete

}
