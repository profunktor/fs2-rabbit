/*
 * Copyright 2017-2024 ProfunKtor
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

package dev.profunktor.fs2rabbit.interpreter

import cats.effect._
import cats.implicits._
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.config.declaration._
import dev.profunktor.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import dev.profunktor.fs2rabbit.model.AckResult.{Ack, NAck, Reject}
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.BaseSpec
import fs2.Stream
import org.scalatest.Assertion

import scala.concurrent.duration._
import scala.util.Random
import scala.concurrent.Future

import cats.effect.unsafe.implicits.global

import cats.effect.kernel.Deferred
import cats.data.Kleisli

trait Fs2RabbitSpec { self: BaseSpec =>

  def config: Fs2RabbitConfig

  val emptyAssertion: Assertion = true shouldBe true

  it should "create a connection and a queue with default arguments" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, _) =>
          declareQueue(DeclarationQueueConfig.default(q)) *>
            declareExchange(x, ExchangeType.Topic)
        }
        .as(emptyAssertion)
    }
  }

  it should "create a connection and a random queue" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      declareQueue.map(_.value should not be empty)
    }
  }

  it should "create a connection and a queue with options enabled" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, _) =>
          declareQueue(DeclarationQueueConfig(q, Durable, Exclusive, AutoDelete, Map.empty)) *>
            declareExchange(x, ExchangeType.Topic)
        }
        .as(emptyAssertion)
    }
  }

  it should "create a connection and a queue (no wait)" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, _) =>
          declareQueueNoWait(DeclarationQueueConfig.default(q)) *>
            declareExchange(x, ExchangeType.Topic)
        }
        .as(emptyAssertion)
    }
  }

  it should "create a connection and a passive queue" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      mkRandomString
        .map(QueueName)
        .flatMap { q =>
          declareQueue(DeclarationQueueConfig.default(q)) *>
            declareQueuePassive(q)
        }
        .as(emptyAssertion)
    }
  }

  it should "return an error during creation of a passive queue if queue doesn't exist" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        queue  <- mkRandomString.map(QueueName)
        result <- declareQueuePassive(queue).attempt
      } yield result.left.value shouldBe a[java.io.IOException]
    }
  }

  it should "return an error during creation of a passive exchange if exchange doesn't exist" in withRabbit {
    interpreter =>
      import interpreter._

      createConnectionChannel.use { implicit channel =>
        for {
          exchange <- mkRandomString.map(ExchangeName)
          result   <- declareExchangePassive(exchange).attempt
        } yield result.left.value shouldBe a[java.io.IOException]
      }
  }

  it should "create a connection and an exchange" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, _) =>
          declareQueue(DeclarationQueueConfig.default(q)) *>
            declareExchange(x, ExchangeType.Topic)
        }
        .as(emptyAssertion)
    }
  }

  it should "create a connection and an exchange (no wait)" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, _) =>
          declareQueue(DeclarationQueueConfig.default(q)) *>
            declareExchangeNoWait(DeclarationExchangeConfig.default(x, ExchangeType.Topic))
        }
        .as(emptyAssertion)
    }
  }

  it should "create a connection and declare an exchange passively" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      mkRandomString
        .map(ExchangeName)
        .flatMap { exchange =>
          declareExchange(exchange, ExchangeType.Topic) *>
            declareExchangePassive(exchange)
        }
        .as(emptyAssertion)
    }
  }

  it should "return an error as the result of 'basicCancel' if consumer doesn't exist" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk      <- randomQueueData
        (q, x, rk) = qxrk
        _         <- declareExchange(x, ExchangeType.Topic)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        ct        <- mkRandomString.map(ConsumerTag)
        result    <- basicCancel(ct).attempt
      } yield result.left.value shouldBe a[java.io.IOException]
    }
  }

  it should "create an acker consumer and verify both envelope and ack result" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk             <- randomQueueData
        (q, x, rk)        = qxrk
        _                <- declareExchange(x, ExchangeType.Topic)
        _                <- declareQueue(DeclarationQueueConfig.default(q))
        _                <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        publisher        <- createPublisher[String](x, rk)
        _                <- publisher("acker-test")
        ackerConsumer    <- createAckerConsumer(q)
        (acker, consumer) = ackerConsumer
        _                <- consumer
                              .take(1)
                              .evalMap { msg =>
                                IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "acker-test")) *>
                                  acker(Ack(msg.deliveryTag))
                              }
                              .compile
                              .drain
      } yield emptyAssertion
    }
  }

  it should "create an acker consumer with ack multiple flag and verify both envelope and ack result" in withRabbit {
    interpreter =>
      import interpreter._

      createConnectionChannel.use { implicit channel =>
        for {
          qxrk             <- randomQueueData
          (q, x, rk)        = qxrk
          _                <- declareExchange(x, ExchangeType.Topic)
          _                <- declareQueue(DeclarationQueueConfig.default(q))
          _                <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
          publisher        <- createPublisher[String](x, rk)
          _                <- publisher("acker-test")
          ackerConsumer    <- createAckerConsumerWithMultipleFlag(q)
          (acker, consumer) = ackerConsumer
          _                <- consumer
                                .take(1)
                                .evalMap { msg =>
                                  IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "acker-test")) *>
                                    acker(Ack(msg.deliveryTag), AckMultiple(true))
                                }
                                .compile
                                .drain
        } yield emptyAssertion
      }
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk             <- randomQueueData
        (q, x, rk)        = qxrk
        _                <- declareExchange(x, ExchangeType.Topic)
        _                <- declareQueue(DeclarationQueueConfig.default(q))
        _                <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        publisher        <- createPublisher[String](x, rk)
        _                <- publisher("NAck-test")
        ackerConsumer    <- createAckerConsumer(q)
        (acker, consumer) = ackerConsumer
        _                <- consumer
                              .take(1)
                              .evalMap { msg =>
                                IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "NAck-test")) *>
                                  acker(NAck(msg.deliveryTag))
                              }
                              .compile
                              .drain
      } yield emptyAssertion
    }
  }

  it should "NOT requeue a message in case of Reject when option 'requeueOnReject = false'" in withRabbit {
    interpreter =>
      import interpreter._

      createConnectionChannel.use { implicit channel =>
        for {
          qxrk             <- randomQueueData
          (q, x, rk)        = qxrk
          _                <- declareExchange(x, ExchangeType.Topic)
          _                <- declareQueue(DeclarationQueueConfig.default(q))
          _                <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
          publisher        <- createPublisher[String](x, rk)
          _                <- publisher("Reject-test")
          ackerConsumer    <- createAckerConsumer(q)
          (acker, consumer) = ackerConsumer
          _                <- consumer
                                .take(1)
                                .evalMap { msg =>
                                  IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "Reject-test")) *>
                                    acker(Reject(msg.deliveryTag))
                                }
                                .compile
                                .drain
        } yield emptyAssertion
      }
  }

  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk      <- randomQueueData
        (q, x, rk) = qxrk
        _         <- declareExchange(x, ExchangeType.Topic)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- bindQueue(q, x, rk)
        publisher <- createPublisher[String](x, rk)
        _         <- publisher("test")
        consumer  <- createAutoAckConsumer(q)
        _         <- consumer
                       .take(1)
                       .evalMap { msg =>
                         IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "test"))
                       }
                       .compile
                       .drain
      } yield emptyAssertion
    }
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk        <- randomQueueData
        (q, x, rk)   = qxrk
        ct          <- mkRandomString.map(ConsumerTag)
        _           <- declareExchange(x, ExchangeType.Topic)
        _           <- declareQueue(DeclarationQueueConfig.default(q))
        _           <- bindQueue(q, x, rk)
        publisher   <- createPublisher[String](x, rk)
        consumerArgs = ConsumerArgs(consumerTag = ct, noLocal = false, exclusive = true, args = Map.empty)
        _           <- publisher("test")
        consumer    <- createAutoAckConsumer(q, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
        _           <- consumer
                         .take(1)
                         .evalMap { msg =>
                           IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "test"))
                         }
                         .compile
                         .drain
      } yield emptyAssertion
    }
  }

  it should "create an exclusive acker consumer with specific BasicQos" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk             <- randomQueueData
        (q, x, rk)        = qxrk
        ct               <- mkRandomString.map(ConsumerTag)
        _                <- declareExchange(x, ExchangeType.Topic)
        _                <- declareQueue(DeclarationQueueConfig.default(q))
        _                <- bindQueue(q, x, rk)
        publisher        <- createPublisher[String](x, rk)
        consumerArgs      = ConsumerArgs(consumerTag = ct, noLocal = false, exclusive = true, args = Map.empty)
        ackerConsumer    <- createAckerConsumer(
                              q,
                              BasicQos(prefetchSize = 0, prefetchCount = 10),
                              Some(consumerArgs)
                            )
        (acker, consumer) = ackerConsumer
        _                <- publisher("test")
        _                <- consumer
                              .take(1)
                              .evalTap { msg =>
                                IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "test")) *>
                                  acker(Ack(msg.deliveryTag))
                              }
                              .compile
                              .drain
      } yield emptyAssertion
    }
  }

  it should "bind a queue with the nowait parameter set to true" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, rk) =>
          declareExchange(x, ExchangeType.Topic) *>
            declareQueue(DeclarationQueueConfig.default(q)) *>
            bindQueueNoWait(q, x, rk, QueueBindingArgs(Map.empty))
        }
        .as(emptyAssertion)
    }
  }

  it should "try to delete a queue twice (only first time should be okay)" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk     <- randomQueueData
        (q, x, _) = qxrk
        _        <- declareExchange(x, ExchangeType.Direct)
        _        <- declareQueue(DeclarationQueueConfig.default(q))
        _        <- deleteQueue(DeletionQueueConfig.default(q))
        _        <- deleteQueueNoWait(DeletionQueueConfig.default(q))
        consumer <- createAutoAckConsumer(q)
        _        <- consumer.attempt
                      .take(1)
                      .evalMap { either =>
                        IO {
                          either shouldBe a[Left[_, _]]
                          either.left.value shouldBe a[java.io.IOException]
                        }
                      }
                      .compile
                      .drain
      } yield emptyAssertion
    }
  }

  it should "delete an exchange successfully" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      mkRandomString.map(ExchangeName).flatMap { exchange =>
        declareExchange(exchange, ExchangeType.Direct) *>
          deleteExchangeNoWait(DeletionExchangeConfig.default(exchange)).attempt.map(_ shouldBe a[Right[_, _]])
      }
    }
  }

  it should "try to unbind a queue when there is no binding" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, rk) =>
          declareExchange(x, ExchangeType.Direct) *>
            declareQueue(DeclarationQueueConfig.default(q)) *>
            unbindQueue(q, x, rk)
        }
        .as(emptyAssertion)
    }
  }

  it should "unbind a queue" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      randomQueueData
        .flatMap { case (q, x, rk) =>
          declareExchange(x, ExchangeType.Direct) *>
            declareQueue(DeclarationQueueConfig.default(q)) *>
            bindQueue(q, x, rk) *>
            unbindQueue(q, x, rk)
        }
        .as(emptyAssertion)
    }
  }

  it should "bind an exchange to another exchange" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      for {
        qxrk             <- randomQueueData
        (q, _, rk)        = qxrk
        sourceExchange   <- mkRandomString.map(ExchangeName)
        destExchange     <- mkRandomString.map(ExchangeName)
        consumerTag      <- mkRandomString.map(ConsumerTag)
        _                <- declareExchange(sourceExchange, ExchangeType.Direct)
        _                <- declareExchange(destExchange, ExchangeType.Direct)
        _                <- declareQueue(DeclarationQueueConfig.default(q))
        _                <- bindQueue(q, destExchange, rk)
        _                <- bindExchange(destExchange, sourceExchange, rk, ExchangeBindingArgs(Map.empty))
        publisher        <- createPublisher[String](sourceExchange, rk)
        consumerArgs      = ConsumerArgs(consumerTag = consumerTag, noLocal = false, exclusive = true, args = Map.empty)
        ackerConsumer    <- createAckerConsumer(
                              q,
                              BasicQos(prefetchSize = 0, prefetchCount = 10),
                              Some(consumerArgs)
                            )
        (acker, consumer) = ackerConsumer
        _                <- publisher("test")
        _                <- consumer
                              .take(1)
                              .evalTap { msg =>
                                IO(msg shouldBe expectedDelivery(msg.deliveryTag, sourceExchange, rk, "test")) *>
                                  acker(Ack(msg.deliveryTag))
                              }
                              .compile
                              .drain
      } yield emptyAssertion
    }
  }

  it should "unbind an exchange" in withRabbit { interpreter =>
    import interpreter._

    createConnectionChannel.use { implicit channel =>
      (mkRandomString.map(RoutingKey), mkRandomString.map(ExchangeName), mkRandomString.map(ExchangeName))
        .mapN { case (rk, sourceExchange, destExchange) =>
          declareExchange(sourceExchange, ExchangeType.Direct) *>
            declareExchange(destExchange, ExchangeType.Direct) *>
            bindExchange(destExchange, sourceExchange, rk) *>
            unbindExchange(destExchange, sourceExchange, rk)
        }
        .as(emptyAssertion)
    }
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in withStreamNackRabbit {
    interpreter =>
      import interpreter._

      Stream.resource(createConnectionChannel).flatMap { implicit channel =>
        for {
          qxrk             <- Stream.eval(randomQueueData)
          (q, x, rk)        = qxrk
          _                <- Stream.eval(declareExchange(x, ExchangeType.Topic))
          _                <- Stream.eval(declareQueue(DeclarationQueueConfig.default(q)))
          _                <- Stream.eval(bindQueue(q, x, rk, QueueBindingArgs(Map.empty)))
          publisher        <- Stream.eval(createPublisher[String](x, rk))
          _                <- Stream.eval(publisher("NAck-test"))
          ackerConsumer    <- Stream.eval(createAckerConsumer(q))
          (acker, consumer) = ackerConsumer
          result           <- Stream.eval(consumer.take(1).compile.lastOrError)
          _                <- Stream.eval(acker(NAck(result.deliveryTag)))
          consumer         <- Stream.eval(createAutoAckConsumer(q))
          result2          <- consumer.take(1) // Message will be re-queued
        } yield {
          val expected = expectedDelivery(result.deliveryTag, x, rk, "NAck-test")

          result shouldBe expected
          result2 shouldBe expected.copy(deliveryTag = result2.deliveryTag, redelivered = true)
        }
      }
  }

  it should "requeue a message in case of Reject when option 'requeueOnReject = true'" in withStreamRejectRabbit {
    interpreter =>
      import interpreter._

      Stream.resource(createConnectionChannel).flatMap { implicit channel =>
        for {
          qxrk             <- Stream.eval(randomQueueData)
          (q, x, rk)        = qxrk
          _                <- Stream.eval(declareExchange(x, ExchangeType.Topic))
          _                <- Stream.eval(declareQueue(DeclarationQueueConfig.default(q)))
          _                <- Stream.eval(bindQueue(q, x, rk, QueueBindingArgs(Map.empty)))
          publisher        <- Stream.eval(createPublisher[String](x, rk))
          _                <- Stream.eval(publisher("Reject-test"))
          ackerConsumer    <- Stream.eval(createAckerConsumer(q))
          (acker, consumer) = ackerConsumer
          result           <- Stream.eval(consumer.take(1).compile.lastOrError)
          _                <- Stream.eval(acker(Reject(result.deliveryTag)))
          consumer         <- Stream.eval(createAutoAckConsumer(q))
          result2          <- consumer.take(1) // Message will be re-queued
        } yield {
          val expected = expectedDelivery(result.deliveryTag, x, rk, "Reject-test")

          result shouldBe expected
          result2 shouldBe expected.copy(deliveryTag = result2.deliveryTag, redelivered = true)
        }
      }
  }

  it should "create a publisher, two auto-ack consumers with different routing keys and assert the routing is correct" in withRabbit {
    interpreter =>
      import interpreter._

      createConnectionChannel.use { implicit channel =>
        for {
          qxrk      <- randomQueueData
          (q, x, rk) = qxrk
          diffQ     <- mkRandomString.map(QueueName)
          _         <- declareExchange(x, ExchangeType.Topic)
          publisher <- createPublisher[String](x, rk)
          _         <- declareQueue(DeclarationQueueConfig.default(q))
          _         <- bindQueue(q, x, rk)
          _         <- declareQueue(DeclarationQueueConfig.default(diffQ))
          _         <- bindQueue(q, x, RoutingKey("diffRK"))
          _         <- publisher("test")
          consumer  <- createAutoAckConsumer(q)
          _         <- consumer
                         .take(1)
                         .evalMap { msg =>
                           createAutoAckConsumer(diffQ).flatMap { c2 =>
                             c2.take(1)
                               .compile
                               .last
                               .timeout(1.second)
                               .attempt
                               .map(_ shouldBe a[Left[_, _]])
                               .as(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "test"))
                           }
                         }
                         .compile
                         .drain
        } yield emptyAssertion
      }
  }

  it should "create a publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in withStreamRabbit {
    interpreter =>
      import interpreter._

      val flag = PublishingFlag(mandatory = true)

      Stream.resource(createConnectionChannel).flatMap { implicit channel =>
        for {
          qxrk      <- Stream.eval(randomQueueData)
          (q, x, rk) = qxrk
          _         <- Stream.eval(declareExchange(x, ExchangeType.Topic))
          _         <- Stream.eval(declareQueue(DeclarationQueueConfig.default(q)))
          _         <- Stream.eval(bindQueue(q, x, rk))
          promise   <- Stream.eval(Deferred[IO, PublishReturn])
          publisher <- Stream
                         .eval(createPublisherWithListener(x, RoutingKey("diff-rk"), flag, listener(promise)))
          _         <- Stream.eval(publisher("test"))
          callback  <- Stream.eval(promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))).unNone
          consumer  <- Stream.eval(createAutoAckConsumer(q))
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

      createConnectionChannel.use { implicit channel =>
        for {
          qxrk      <- randomQueueData
          (q, x, rk) = qxrk
          _         <- declareExchange(x, ExchangeType.Topic)
          _         <- declareQueue(DeclarationQueueConfig.default(q))
          _         <- bindQueue(q, x, rk)
          publisher <- createRoutingPublisher[String](x)
          _         <- publisher(rk).apply("test")
          consumer  <- createAutoAckConsumer(q)
          _         <- consumer
                         .take(1)
                         .evalMap { msg =>
                           IO(msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, "test"))
                         }
                         .compile
                         .drain
        } yield emptyAssertion
      }
  }

  it should "create a routing publisher with listener and flag 'mandatory=true', an auto-ack consumer, publish a message and return to the listener" in withStreamRabbit {
    interpreter =>
      import interpreter._

      val flag = PublishingFlag(mandatory = true)

      Stream.resource(createConnectionChannel).flatMap { implicit channel =>
        for {
          qxrk      <- Stream.eval(randomQueueData)
          (q, x, rk) = qxrk
          _         <- Stream.eval(declareExchange(x, ExchangeType.Topic))
          _         <- Stream.eval(declareQueue(DeclarationQueueConfig.default(q)))
          _         <- Stream.eval(bindQueue(q, x, rk))
          promise   <- Stream.eval(Deferred[IO, PublishReturn])
          publisher <- Stream.eval(createRoutingPublisherWithListener[String](x, flag, listener(promise)))
          _         <- Stream.eval(publisher(RoutingKey("diff-rk"))("test"))
          callback  <- Stream.eval(promise.get.map(_.some).timeoutTo(500.millis, IO.pure(none[PublishReturn]))).unNone
          consumer  <- Stream.eval(createAutoAckConsumer(q))
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

    val message = "Test text here"

    def consumer(q: QueueName, x: ExchangeName, rk: RoutingKey): IO[Option[AmqpEnvelope[String]]] =
      createConnectionChannel.use { implicit channel =>
        for {
          _        <- declareQueue(DeclarationQueueConfig.default(q))
          _        <- declareExchange(x, ExchangeType.Topic)
          _        <- bindQueue(q, x, rk)
          consumer <- createAutoAckConsumer(q)
          msg      <- consumer.take(1).compile.last
        } yield msg
      }

    def producer(q: QueueName, x: ExchangeName, rk: RoutingKey): IO[Unit] =
      createConnectionChannel.use { implicit channel =>
        for {
          _         <- declareQueue(DeclarationQueueConfig.default(q))
          _         <- declareExchange(x, ExchangeType.Topic)
          _         <- bindQueue(q, x, rk)
          publisher <- createPublisher[String](x, rk)
          _         <- publisher(message)
        } yield ()
      }

    randomQueueData
      .flatMap { case (q, x, rk) =>
        producer(q, x, rk) *>
          consumer(q, x, rk).map {
            case Some(msg) =>
              msg shouldBe expectedDelivery(msg.deliveryTag, x, rk, message)
            case _         => emptyAssertion
          }
      }
  }

  it should "shutdown the stream when the server closes the channel" in withRabbit { interpreter =>
    import interpreter._
    val msg = "will-not-be-acked"
    createConnectionChannel.use { implicit channel =>
      for {
        qxrk      <- randomQueueData
        (q, x, rk) = qxrk
        _         <- declareExchange(x, ExchangeType.Topic)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- bindQueue(q, x, rk, QueueBindingArgs(Map.empty))
        publisher <- createPublisher[String](x, rk)
        _         <- publisher(msg)
        stream    <- createAckerConsumer(q).map(_._2)
        results   <- stream.attempt.compile.toList.timeoutAndForget(Duration(10, "s"))
      } yield {
        results.size shouldEqual 2
        results.head.map(_.payload) shouldEqual Right(msg)
        results.last.isLeft shouldEqual true
      }
    }
  }

  it should "preserve order of published messages" in withRabbit { interpreter =>
    import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
    import interpreter._

    implicit val intMessageEncoder: MessageEncoder[IO, AmqpMessage[Int]] =
      Kleisli[IO, AmqpMessage[Int], AmqpMessage[Array[Byte]]](s =>
        IO.pure(s.copy(payload = s.payload.toString.getBytes))
      )
    implicit val intMessageDecoder: EnvelopeDecoder[IO, Int]             =
      Kleisli[IO, AmqpEnvelope[Array[Byte]], Int](s => IO.pure(new String(s.payload).toInt))

    def simpleMessage(i: Int) =
      AmqpMessage(
        i,
        AmqpProperties(headers =
          Map("demoId" -> AmqpFieldValue.LongVal(123), "app" -> AmqpFieldValue.StringVal("fs2RabbitTest"))
        )
      )

    createConnectionChannel.use { implicit channel =>
      val n = 100
      for {
        qxrk      <- randomQueueData
        (q, x, rk) = qxrk
        _         <- declareExchange(x, ExchangeType.Topic)
        _         <- declareQueue(DeclarationQueueConfig.default(q))
        _         <- bindQueue(q, x, rk)
        publisher <- createPublisher[AmqpMessage[Int]](x, rk)
        _         <- Stream
                       .iterable(0 to n)
                       .evalTap(i => publisher(simpleMessage(i)))
                       .compile
                       .drain
        consumer  <- createAutoAckConsumer[Int](q)
        is        <- consumer
                       .take(n.toLong)
                       .compile
                       .toList
                       .map(_.map(_.payload))
      } yield is shouldBe sorted
    }
  }

  private def withStreamRabbit[A](fa: RabbitClient[IO] => Stream[IO, A]): Future[Assertion] =
    RabbitClient
      .default[IO](config)
      .resource
      .use(r => fa(r).compile.drain)
      .as(emptyAssertion)
      .unsafeToFuture()

  private def withStreamNackRabbit[A](fa: RabbitClient[IO] => Stream[IO, A]): Future[Assertion] =
    RabbitClient
      .default[IO](config.copy(requeueOnNack = true))
      .resource
      .use(r => fa(r).compile.drain)
      .as(emptyAssertion)
      .unsafeToFuture()

  private def withStreamRejectRabbit[A](fa: RabbitClient[IO] => Stream[IO, A]): Future[Assertion] =
    RabbitClient
      .default[IO](config.copy(requeueOnReject = true))
      .resource
      .use(r => fa(r).compile.drain)
      .as(emptyAssertion)
      .unsafeToFuture()

  private def withRabbit[A](fa: RabbitClient[IO] => IO[A]): Future[A] =
    RabbitClient
      .default[IO](config)
      .resource
      .use(r => fa(r))
      .unsafeToFuture()

  private def randomQueueData: IO[(QueueName, ExchangeName, RoutingKey)] =
    (mkRandomString, mkRandomString, mkRandomString).mapN { case (a, b, c) =>
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

  private def listener(promise: Deferred[IO, PublishReturn]): PublishReturn => IO[Unit] = promise.complete(_).void

}
