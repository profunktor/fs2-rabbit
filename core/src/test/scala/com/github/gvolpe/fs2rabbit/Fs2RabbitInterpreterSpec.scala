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

//package com.github.gvolpe.fs2rabbit
//
//import cats.effect.IO
//import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
//import com.github.gvolpe.fs2rabbit.embedded.EmbeddedAmqpBroker
//import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
//import com.github.gvolpe.fs2rabbit.model._
//import fs2._
//import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}
//
//import scala.concurrent.ExecutionContext.Implicits.global
//
//// To take into account: https://www.rabbitmq.com/interoperability.html
//class Fs2RabbitInterpreterSpec extends FlatSpecLike with Matchers with BeforeAndAfterEach {
//
//  private val config = IO(
//    Fs2RabbitConfig("localhost",
//                    45947,
//                    "hostnameAlias",
//                    3,
//                    useSsl = false,
//                    requeueOnNack = false,
//                    username = None,
//                    password = None))
//  private val nackConfig = IO(
//    Fs2RabbitConfig("localhost",
//                    45947,
//                    "hostnameAlias",
//                    3,
//                    useSsl = false,
//                    requeueOnNack = true,
//                    username = None,
//                    password = None))
//
//  private val fs2RabbitInterpreter     = new Fs2Rabbit[IO](config)
//  private val fs2RabbitNackInterpreter = new Fs2Rabbit[IO](nackConfig)
//
//  // Workaround to let the Qpid Broker close the connection between tests
//  override def beforeEach(): Unit =
//    Thread.sleep(300)
//
//  private val exchangeName = "ex".as[ExchangeName]
//  private val queueName    = "daQ".as[QueueName]
//  private val routingKey   = "rk".as[RoutingKey]
//
//  it should "create a connection, a channel, a queue and an exchange" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          queueD     <- declareQueue(queueName)
//          _          <- declareExchange(exchangeName, ExchangeType.Topic)
//          connection = channel.getConnection
//        } yield {
//          channel.getChannelNumber should be(1)
//          queueD.getQueue should be(queueName.value)
//          connection.getAddress.isLoopbackAddress should be(true)
//          connection.toString should startWith("amqp://guest@")
//          connection.toString should endWith("45947/hostnameAlias")
//          broker
//        }
//      }
//    }
//  }
//
//  it should "create an acker consumer and verify both envelope and ack result" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
//          ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
//          _                 <- declareExchange(exchangeName, ExchangeType.Direct)
//          _                 <- declareQueue(queueName)
//          _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
//          publisher         <- createPublisher(exchangeName, routingKey)
//          msg               = Stream(AmqpMessage("acker-test", AmqpProperties.empty))
//          _                 <- msg.covary[IO] to publisher
//          ackerConsumer     <- createAckerConsumer(queueName)
//          (acker, consumer) = ackerConsumer
//          _ <- Stream(
//                consumer to testQ.enqueue,
//                Stream(Ack(new DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker
//              ).join(2).take(1)
//          result    <- Stream.eval(testQ.dequeue1)
//          ackResult <- Stream.eval(ackerQ.dequeue1)
//        } yield {
//          result should be(
//            AmqpEnvelope(new DeliveryTag(1),
//                         "acker-test",
//                         AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
//          ackResult should be(Ack(new DeliveryTag(1)))
//          broker
//        }
//      }
//    }
//  }
//
//  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
//          ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
//          _                 <- declareExchange(exchangeName, ExchangeType.Direct)
//          _                 <- declareQueue(queueName)
//          _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
//          publisher         <- createPublisher(exchangeName, routingKey)
//          msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
//          _                 <- msg.covary[IO] to publisher
//          ackerConsumer     <- createAckerConsumer(queueName)
//          (acker, consumer) = ackerConsumer
//          _                 <- (consumer to testQ.enqueue).take(1)
//          _ <- (Stream(NAck(new DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker)
//                .take(1)
//          result    <- Stream.eval(testQ.dequeue1)
//          ackResult <- Stream.eval(ackerQ.dequeue1)
//        } yield {
//          result should be(
//            AmqpEnvelope(new DeliveryTag(1), "NAck-test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
//          ackResult should be(NAck(new DeliveryTag(1)))
//          broker
//        }
//      }
//    }
//  }
//
//  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in StreamAssertion {
//    import fs2RabbitNackInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
//          ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
//          _                 <- declareExchange(exchangeName, ExchangeType.Direct)
//          _                 <- declareQueue(queueName)
//          _                 <- bindQueue(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
//          publisher         <- createPublisher(exchangeName, routingKey)
//          msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
//          _                 <- msg.covary[IO] to publisher
//          ackerConsumer     <- createAckerConsumer(queueName)
//          (acker, consumer) = ackerConsumer
//          _                 <- (consumer to testQ.enqueue).take(2) // Message will be requeued
//          _ <- (Stream(NAck(new DeliveryTag(1))).covary[IO].observe(ackerQ.enqueue) to acker)
//                .take(1)
//          result    <- testQ.dequeue.take(1)
//          ackResult <- ackerQ.dequeue.take(1)
//        } yield {
//          result shouldBe an[AmqpEnvelope]
//          ackResult should be(NAck(new DeliveryTag(1)))
//          broker
//        }
//      }
//    }
//  }
//
//  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
//          _         <- declareExchange(exchangeName, ExchangeType.Direct)
//          _         <- declareQueue(queueName)
//          _         <- bindQueue(queueName, exchangeName, routingKey)
//          publisher <- createPublisher(exchangeName, routingKey)
//          consumer  <- createAutoAckConsumer(queueName)
//          msg       = Stream(AmqpMessage("test", AmqpProperties.empty))
//          _         <- msg.covary[IO] to publisher
//          _         <- (consumer to testQ.enqueue).take(1)
//          result    <- Stream.eval(testQ.dequeue1)
//        } yield {
//          result should be(
//            AmqpEnvelope(new DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
//          broker
//        }
//      }
//    }
//  }
//
//  it should "create an exclusive auto-ack consumer with specific BasicQos" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
//          _         <- declareExchange(exchangeName, ExchangeType.Direct)
//          _         <- declareQueue(queueName)
//          _         <- bindQueue(queueName, exchangeName, routingKey)
//          publisher <- createPublisher(exchangeName, routingKey)
//          consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
//                                      noLocal = false,
//                                      exclusive = true,
//                                      args = Map.empty[String, AnyRef])
//          consumer <- createAutoAckConsumer(queueName,
//                                            BasicQos(prefetchSize = 0, prefetchCount = 10),
//                                            Some(consumerArgs))
//          msg    = Stream(AmqpMessage("test", AmqpProperties.empty))
//          _      <- msg.covary[IO] to publisher
//          _      <- (consumer to testQ.enqueue).take(1)
//          result <- Stream.eval(testQ.dequeue1)
//        } yield {
//          println(consumer)
//          result should be(
//            AmqpEnvelope(new DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
//          broker
//        }
//      }
//    }
//  }
//
//  it should "create an exclusive acker consumer with specific BasicQos" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
//          ackerQ    <- Stream.eval(async.boundedQueue[IO, AckResult](100))
//          _         <- declareExchange(exchangeName, ExchangeType.Direct)
//          _         <- declareQueue(queueName)
//          _         <- bindQueue(queueName, exchangeName, routingKey)
//          publisher <- createPublisher(exchangeName, routingKey)
//          consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
//                                      noLocal = false,
//                                      exclusive = true,
//                                      args = Map.empty[String, AnyRef])
//          ackerConsumer <- createAckerConsumer(queueName,
//                                               BasicQos(prefetchSize = 0, prefetchCount = 10),
//                                               Some(consumerArgs))
//          (acker, consumer) = ackerConsumer
//          msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
//          _                 <- msg.covary[IO] to publisher
//          _ <- Stream(
//                consumer to testQ.enqueue,
//                Stream(Ack(new DeliveryTag(1))).covary[IO] observe ackerQ.enqueue to acker
//              ).join(2).take(1)
//          result    <- Stream.eval(testQ.dequeue1)
//          ackResult <- Stream.eval(ackerQ.dequeue1)
//        } yield {
//          result should be(
//            AmqpEnvelope(new DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
//          ackResult should be(Ack(new DeliveryTag(1)))
//          broker
//        }
//      }
//    }
//  }
//
//  it should "bind a queue with the nowait parameter set to true" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          _ <- declareExchange(exchangeName, ExchangeType.Direct)
//          _ <- declareQueue(queueName)
//          _ <- bindQueueNoWait(queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
//        } yield broker
//      }
//    }
//  }
//
//  it should "delete a queue" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          _        <- declareExchange(exchangeName, ExchangeType.Direct)
//          _        <- declareQueue(queueName)
//          _        <- deleteQueue(queueName)
//          consumer <- createAutoAckConsumer(queueName)
//          either   <- consumer.attempt
//        } yield {
//          either shouldBe a[Left[_, _]]
//          either.left.get shouldBe a[java.io.IOException]
//          broker
//        }
//      }
//    }
//  }
//
//  it should "fail to unbind a queue when there is no binding" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          _      <- declareExchange(exchangeName, ExchangeType.Direct)
//          _      <- declareQueue(queueName)
//          either <- unbindQueue(queueName, exchangeName, routingKey).attempt
//        } yield {
//          either shouldBe a[Left[_, _]]
//          either.left.get shouldBe a[java.io.IOException]
//          broker
//        }
//      }
//    }
//  }
//
//  it should "unbind a queue" in StreamAssertion {
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          _ <- declareExchange(exchangeName, ExchangeType.Direct)
//          _ <- declareQueue(queueName)
//          _ <- bindQueue(queueName, exchangeName, routingKey)
//          _ <- unbindQueue(queueName, exchangeName, routingKey)
//        } yield broker
//      }
//    }
//  }
//
//  // Operation not supported by the Embedded Broker
//  ignore should "bind an exchange to another exchange" in StreamAssertion {
//    val sourceExchangeName      = "sourceExchange".as[ExchangeName]
//    val destinationExchangeName = "destinationExchange".as[ExchangeName]
//
//    import fs2RabbitInterpreter._
//    EmbeddedAmqpBroker.createBroker flatMap { broker =>
//      createConnectionChannel flatMap { implicit channel =>
//        for {
//          testQ  <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
//          ackerQ <- Stream.eval(async.boundedQueue[IO, AckResult](100))
//          _      <- declareExchange(sourceExchangeName, ExchangeType.Direct)
//          _      <- declareExchange(destinationExchangeName, ExchangeType.Direct)
//          _      <- declareQueue(queueName)
//          _      <- bindQueue(queueName, destinationExchangeName, routingKey)
//          _ <- bindExchange(destinationExchangeName,
//                            sourceExchangeName,
//                            routingKey,
//                            ExchangeBindingArgs(Map.empty[String, AnyRef]))
//          publisher <- createPublisher(sourceExchangeName, routingKey)
//          consumerArgs = ConsumerArgs(consumerTag = "XclusiveConsumer",
//                                      noLocal = false,
//                                      exclusive = true,
//                                      args = Map.empty[String, AnyRef])
//          ackerConsumer <- createAckerConsumer(queueName,
//                                               BasicQos(prefetchSize = 0, prefetchCount = 10),
//                                               Some(consumerArgs))
//          (acker, consumer) = ackerConsumer
//          msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
//          _                 <- msg.covary[IO] to publisher
//          _ <- Stream(
//                consumer to testQ.enqueue,
//                Stream(Ack(new DeliveryTag(1))).covary[IO] observe ackerQ.enqueue to acker
//              ).join(2).take(1)
//          result    <- Stream.eval(testQ.dequeue1)
//          ackResult <- Stream.eval(ackerQ.dequeue1)
//        } yield {
//          result should be(
//            AmqpEnvelope(new DeliveryTag(1), "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
//          ackResult should be(Ack(new DeliveryTag(1)))
//          broker
//        }
//      }
//    }
//  }
//
//}
