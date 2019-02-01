/*
 * Copyright 2017-2019 Gabriel Volpe
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
import cats.effect.IO
import cats.syntax.apply._
import cats.syntax.option._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.config.declaration._
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.model.AckResult.{Ack, NAck}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.{BaseSpec, IOAssertion, model}
import fs2.Stream

import scala.concurrent.duration._
import scala.util.Random

trait Fs2RabbitSpec { self: BaseSpec =>

  def config: Fs2RabbitConfig

  it should "create a connection and a queue with default arguments" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, _) <- Stream.eval(randomQueueData)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- declareExchange(x, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a queue with options enabled" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, _) <- Stream.eval(randomQueueData)
        _         <- declareQueue(DeclarationQueueConfig(q, Durable, Exclusive, AutoDelete, Map.empty))
        _         <- declareExchange(x, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a queue (no wait)" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, _) <- Stream.eval(randomQueueData)
        _         <- declareQueueNoWait(DeclarationQueueConfig.default(q))
        _         <- declareExchange(x, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and a passive queue" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        q <- Stream.eval(mkRandomString.map(QueueName))
        _ <- declareQueue(DeclarationQueueConfig.default(q))
        _ <- declareQueuePassive(q)
      } yield ()
    }
  }

  it should "return an error during creation of a passive queue if queue doesn't exist" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        queue  <- Stream.eval(mkRandomString.map(QueueName))
        result <- declareQueuePassive(queue).attempt
      } yield {
        result.left.value shouldBe a[java.io.IOException]
      }
    }
  }

  it should "return an error during creation of a passive exchange if exchange doesn't exist" in withRabbit {
    interpreter =>
      import interpreter._

      createConnectionChannel.flatMap { implicit channel =>
        for {
          exchange <- Stream.eval(mkRandomString.map(ExchangeName))
          result   <- declareExchangePassive(exchange).attempt
        } yield {
          result.left.value shouldBe a[java.io.IOException]
        }
      }
  }

  it should "create a connection and an exchange" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, _) <- Stream.eval(randomQueueData)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- declareExchange(x, ExchangeType.Topic)
      } yield ()
    }
  }

  it should "create a connection and an exchange (no wait)" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, _) <- Stream.eval(randomQueueData)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- declareExchangeNoWait(DeclarationExchangeConfig.default(x, ExchangeType.Topic))
      } yield ()
    }
  }

  it should "create a connection and declare an exchange passively" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        exchange <- Stream.eval(mkRandomString.map(ExchangeName))
        _        <- declareExchange(exchange, ExchangeType.Topic)
        _        <- declareExchangePassive(exchange)
      } yield ()
    }
  }

  it should "return an error as the result of 'basicCancel' if consumer doesn't exist" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk) <- Stream.eval(randomQueueData)
        _          <- declareExchange(x, ExchangeType.Topic)
        _          <- declareQueue(DeclarationQueueConfig.default(q))
        _          <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        ct         <- Stream.eval(mkRandomString.map(ConsumerTag))
        result     <- Stream.eval(basicCancel(ct)).attempt.take(1)
      } yield {
        result.left.value shouldBe a[java.io.IOException]
      }
    }
  }

  it should "create an acker consumer and verify both envelope and ack result" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk)        <- Stream.eval(randomQueueData)
        _                 <- declareExchange(x, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(q))
        _                 <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        publisher         <- createPublisher[String](x, rk)
        _                 <- Stream.eval(publisher("acker-test"))
        (acker, consumer) <- createAckerConsumer(q)
        result            <- consumer.take(1)
        _                 <- Stream.eval(acker(Ack(result.deliveryTag)))
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, x, rk, "acker-test")
      }
    }
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk)        <- Stream.eval(randomQueueData)
        _                 <- declareExchange(x, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(q))
        _                 <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        publisher         <- createPublisher[String](x, rk)
        _                 <- Stream.eval(publisher("NAck-test"))
        (acker, consumer) <- createAckerConsumer(q)
        result            <- consumer.take(1)
        _                 <- Stream.eval(acker(NAck(result.deliveryTag)))
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, x, rk, "NAck-test")
      }
    }
  }

  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk) <- Stream.eval(randomQueueData)
        _          <- declareExchange(x, ExchangeType.Topic)
        _          <- declareQueue(DeclarationQueueConfig.default(q))
        _          <- bindQueue(q, x, rk)
        publisher  <- createPublisher[String](x, rk)
        consumer   <- createAutoAckConsumer(q)
        _          <- Stream.eval(publisher("test"))
        result     <- consumer.take(1)
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, x, rk, "test")
      }
    }
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk)   <- Stream.eval(randomQueueData)
        ct           <- Stream.eval(mkRandomString.map(ConsumerTag))
        _            <- declareExchange(x, ExchangeType.Topic)
        _            <- declareQueue(DeclarationQueueConfig.default(q))
        _            <- bindQueue(q, x, rk)
        publisher    <- createPublisher[String](x, rk)
        consumerArgs = ConsumerArgs(consumerTag = ct, noLocal = false, exclusive = true, args = Map.empty)
        consumer     <- createAutoAckConsumer(q, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
        _            <- Stream.eval(publisher("test"))
        result       <- consumer.take(1)
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, x, rk, "test")
      }
    }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk)        <- Stream.eval(randomQueueData)
        ct                <- Stream.eval(mkRandomString.map(ConsumerTag))
        _                 <- declareExchange(x, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(q))
        _                 <- bindQueue(q, x, rk)
        publisher         <- createPublisher[String](x, rk)
        consumerArgs      = ConsumerArgs(consumerTag = ct, noLocal = false, exclusive = true, args = Map.empty)
        (acker, consumer) <- createAckerConsumer(q, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
        _                 <- Stream.eval(publisher("test"))
        result            <- consumer.take(1)
        _                 <- Stream.eval(acker(Ack(result.deliveryTag)))
      } yield {
        result shouldBe expectedDelivery(result.deliveryTag, x, rk, "test")
      }
    }
  }

  it should "bind a queue with the nowait parameter set to true" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk) <- Stream.eval(randomQueueData)
        _          <- declareExchange(x, ExchangeType.Topic)
        _          <- declareQueue(DeclarationQueueConfig.default(q))
        _          <- bindQueueNoWait(q, x, rk, QueueBindingArgs(Map.empty))
      } yield ()
    }
  }

  it should "try to delete a queue twice (only first time should be okay)" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, _) <- Stream.eval(randomQueueData)
        _         <- declareExchange(x, ExchangeType.Direct)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- deleteQueue(DeletionQueueConfig.default(q))
        _         <- deleteQueueNoWait(DeletionQueueConfig.default(q))
        consumer  <- createAutoAckConsumer(q)
        either    <- consumer.attempt.take(1)
      } yield {
        either shouldBe a[Left[_, _]]
        either.left.value shouldBe a[java.io.IOException]
      }
    }
  }

  it should "delete an exchange successfully" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        exchange <- Stream.eval(mkRandomString.map(ExchangeName))
        _        <- declareExchange(exchange, ExchangeType.Direct)
        either   <- deleteExchangeNoWait(DeletionExchangeConfig.default(exchange)).attempt.take(1)
      } yield {
        either shouldBe a[Right[_, _]]
      }
    }
  }

  it should "try to unbind a queue when there is no binding" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk) <- Stream.eval(randomQueueData)
        _          <- declareExchange(x, ExchangeType.Direct)
        _          <- declareQueue(DeclarationQueueConfig.default(q))
        _          <- unbindQueue(q, x, rk)
      } yield ()
    }
  }

  it should "unbind a queue" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk) <- Stream.eval(randomQueueData)
        _          <- declareExchange(x, ExchangeType.Direct)
        _          <- declareQueue(DeclarationQueueConfig.default(q))
        _          <- bindQueue(q, x, rk)
        _          <- unbindQueue(q, x, rk)
      } yield ()
    }
  }

  it should "bind an exchange to another exchange" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, _, rk)        <- Stream.eval(randomQueueData)
        sourceExchange    <- Stream.eval(mkRandomString.map(ExchangeName))
        destExchange      <- Stream.eval(mkRandomString.map(ExchangeName))
        consumerTag       <- Stream.eval(mkRandomString.map(ConsumerTag))
        _                 <- declareExchange(sourceExchange, ExchangeType.Direct)
        _                 <- declareExchange(destExchange, ExchangeType.Direct)
        _                 <- declareQueue(DeclarationQueueConfig.default(q))
        _                 <- bindQueue(q, destExchange, rk)
        _                 <- bindExchange(destExchange, sourceExchange, rk, ExchangeBindingArgs(Map.empty))
        publisher         <- createPublisher[String](sourceExchange, rk)
        consumerArgs      = ConsumerArgs(consumerTag = consumerTag, noLocal = false, exclusive = true, args = Map.empty)
        (acker, consumer) <- createAckerConsumer(q, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
        _                 <- Stream.eval(publisher("test"))
        receivedMessage   <- consumer.take(1)
        _                 <- Stream.eval(acker(Ack(receivedMessage.deliveryTag)))
      } yield {
        receivedMessage shouldBe expectedDelivery(receivedMessage.deliveryTag, sourceExchange, rk, "test")
      }
    }
  }

  it should "unbind an exchange" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        rk             <- Stream.eval(mkRandomString.map(RoutingKey))
        sourceExchange <- Stream.eval(mkRandomString.map(ExchangeName))
        destExchange   <- Stream.eval(mkRandomString.map(ExchangeName))
        _              <- declareExchange(sourceExchange, ExchangeType.Direct)
        _              <- declareExchange(destExchange, ExchangeType.Direct)
        _              <- bindExchange(destExchange, sourceExchange, rk)
        _              <- unbindExchange(destExchange, sourceExchange, rk)
      } yield ()
    }
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in withNackRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.flatMap { implicit channel =>
      for {
        (q, x, rk)        <- Stream.eval(randomQueueData)
        _                 <- declareExchange(x, ExchangeType.Topic)
        _                 <- declareQueue(DeclarationQueueConfig.default(q))
        _                 <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        publisher         <- createPublisher[String](x, rk)
        _                 <- Stream.eval(publisher("NAck-test"))
        (acker, consumer) <- createAckerConsumer(q)
        result            <- Stream.eval(consumer.take(1).compile.lastOrError)
        _                 <- Stream.eval(acker(NAck(result.deliveryTag)))
        result2           <- createAutoAckConsumer(q).flatten.take(1) // Message will be re-queued
      } yield {
        val expected = expectedDelivery(result.deliveryTag, x, rk, "NAck-test")

        result shouldBe expected
        result2 shouldBe expected.copy(deliveryTag = result2.deliveryTag, redelivered = true)
      }
    }
  }

  it should "create a publisher, two auto-ack consumers with different routing keys and assert the routing is correct" in withRabbit {
    interpreter =>
      import interpreter._

      createConnectionChannel.flatMap { implicit channel =>
        for {
          (q, x, rk) <- Stream.eval(randomQueueData)
          diffQ      <- Stream.eval(mkRandomString.map(QueueName))
          _          <- declareExchange(x, ExchangeType.Topic)
          publisher  <- createPublisher[String](x, rk)
          _          <- declareQueue(DeclarationQueueConfig.default(q))
          _          <- bindQueue(q, x, rk)
          _          <- declareQueue(DeclarationQueueConfig.default(diffQ))
          _          <- bindQueue(q, x, RoutingKey("diffRK"))
          c1         <- createAutoAckConsumer(q)
          c2         <- createAutoAckConsumer(diffQ)
          _          <- Stream.eval(publisher("test"))
          result     <- c1.take(1)
          rs2        <- takeWithTimeOut(c2, 1.second)
        } yield {
          result shouldBe expectedDelivery(result.deliveryTag, x, rk, "test")
          rs2 shouldBe None
        }
      }
  }

  it should "create a publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in withRabbit {
    interpreter =>
      import interpreter._

      val flag = PublishingFlag(mandatory = true)

      createConnectionChannel.flatMap { implicit channel =>
        for {
          (q, x, rk) <- Stream.eval(randomQueueData)
          _          <- declareExchange(x, ExchangeType.Topic)
          _          <- declareQueue(DeclarationQueueConfig.default(q))
          _          <- bindQueue(q, x, rk)
          promise    <- Stream.eval(Deferred[IO, PublishReturn])
          publisher  <- createPublisherWithListener(x, RoutingKey("diff-rk"), flag, listener(promise))
          consumer   <- createAutoAckConsumer(q)
          _          <- Stream.eval(publisher("test"))
          callback   <- Stream.eval(promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))).unNone
          result     <- takeWithTimeOut(consumer, 500.millis)
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

      createConnectionChannel.flatMap { implicit channel =>
        for {
          (q, x, rk) <- Stream.eval(randomQueueData)
          _          <- declareExchange(x, ExchangeType.Topic)
          _          <- declareQueue(DeclarationQueueConfig.default(q))
          _          <- bindQueue(q, x, rk)
          publisher  <- createRoutingPublisher[String](x)
          consumer   <- createAutoAckConsumer(q)
          _          <- Stream.eval(publisher(rk).apply("test"))
          result     <- consumer.take(1)
        } yield {
          result shouldBe expectedDelivery(result.deliveryTag, x, rk, "test")
        }
      }
  }

  it should "create a routing publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in withRabbit {
    interpreter =>
      import interpreter._

      val flag = PublishingFlag(mandatory = true)

      createConnectionChannel.flatMap { implicit channel =>
        for {
          (q, x, rk) <- Stream.eval(randomQueueData)
          _          <- declareExchange(x, ExchangeType.Topic)
          _          <- declareQueue(DeclarationQueueConfig.default(q))
          _          <- bindQueue(q, x, rk)
          promise    <- Stream.eval(Deferred[IO, PublishReturn])
          publisher  <- createRoutingPublisherWithListener[String](x, flag, listener(promise))
          consumer   <- createAutoAckConsumer(q)
          _          <- Stream.eval(publisher(RoutingKey("diff-rk"))("test"))
          callback   <- Stream.eval(promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))).unNone
          result     <- takeWithTimeOut(consumer, 500.millis)
        } yield {
          result shouldBe None
          callback.body.value shouldEqual "test".getBytes("UTF-8")
          callback.routingKey shouldBe RoutingKey("diff-rk")
        }
      }
  }

  it should "consume published message" in withRabbit { interpreter =>
    import interpreter._

    val message = "Test text here"

    def consumer(q: QueueName, x: ExchangeName, rk: RoutingKey): Stream[IO, model.AmqpEnvelope[String]] =
      createConnectionChannel flatMap { implicit channel =>
        for {
          _        <- declareQueue(DeclarationQueueConfig.default(q))
          _        <- declareExchange(x, ExchangeType.Topic)
          _        <- bindQueue(q, x, rk)
          consumer <- createAutoAckConsumer[String](q)
          message  <- consumer
        } yield message
      }

    def producer(q: QueueName, x: ExchangeName, rk: RoutingKey): Stream[IO, Unit] =
      createConnectionChannel flatMap { implicit channel =>
        for {
          _         <- declareQueue(DeclarationQueueConfig.default(q))
          _         <- declareExchange(x, ExchangeType.Topic)
          _         <- bindQueue(q, x, rk)
          publisher <- createPublisher[String](x, rk)
          _         <- Stream.eval(publisher(message))
        } yield ()
      }

    for {
      (q, x, rk)      <- Stream.eval(randomQueueData)
      _               <- producer(q, x, rk)
      receivedMessage <- consumer(q, x, rk).take(1)
    } yield {
      receivedMessage shouldBe expectedDelivery(receivedMessage.deliveryTag, x, rk, message)
    }
  }

  private def withRabbit[A](fa: Fs2Rabbit[IO] => Stream[IO, A]): Unit = IOAssertion {
    Fs2Rabbit[IO](config).flatMap(r => fa(r).compile.drain)
  }

  private def withNackRabbit[A](fa: Fs2Rabbit[IO] => Stream[IO, A]): Unit = IOAssertion {
    Fs2Rabbit[IO](config.copy(requeueOnNack = true)).flatMap(r => fa(r).compile.drain)
  }

  private def randomQueueData: IO[(QueueName, ExchangeName, RoutingKey)] =
    (mkRandomString, mkRandomString, mkRandomString).mapN {
      case (a, b, c) =>
        (QueueName(a), ExchangeName(b), RoutingKey(c))
    }

  private def mkRandomString: IO[String] = IO(Random.alphanumeric.take(6).mkString.toLowerCase)

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
    stream.last.mergeHaltR(Stream.sleep[IO](timeout).as(None))

  private def listener(promise: Deferred[IO, PublishReturn]): PublishReturn => IO[Unit] = promise.complete

}
