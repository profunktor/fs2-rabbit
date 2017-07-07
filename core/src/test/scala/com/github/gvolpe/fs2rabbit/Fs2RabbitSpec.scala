package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.embedded.EmbeddedAmqpBroker
import com.github.gvolpe.fs2rabbit.model._
import fs2._
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

// To take into account: https://www.rabbitmq.com/interoperability.html
class Fs2RabbitSpec extends FlatSpecLike with Matchers with BeforeAndAfterEach {

  behavior of "Fs2Rabbit"

  object TestFs2Rabbit extends Fs2Rabbit with UnderlyingAmqpClient {
    override protected val log = LoggerFactory.getLogger(getClass)
    override protected val fs2RabbitConfig =
      Fs2RabbitConfig("localhost", 45947, "hostnameAlias", 3, requeueOnNack = false)
  }

  object TestNAckFs2Rabbit extends Fs2Rabbit with UnderlyingAmqpClient {
    override protected val log = LoggerFactory.getLogger(getClass)
    override protected val fs2RabbitConfig =
      Fs2RabbitConfig("localhost", 45947, "hostnameAlias", 3, requeueOnNack = true)
  }

  // Workaround to let the Qpid Broker close the connection between tests
  override def beforeEach() = {
    Thread.sleep(300)
  }

  val exchangeName  = ExchangeName("ex")
  val queueName     = QueueName("daQ")
  val routingKey    = RoutingKey("rk")

  it should "create a connection, a channel, a queue and an exchange" in {
    import TestFs2Rabbit._

    val program = for {
      broker  <- EmbeddedAmqpBroker.createBroker
      channel <- createConnectionChannel[IO]()
      queueD  <- declareQueue[IO](channel, queueName)
      _       <- declareExchange[IO](channel, exchangeName, ExchangeType.Topic)
    } yield {
      channel.getConnection.toString  should be ("amqp://guest@127.0.0.1:45947/hostnameAlias")
      channel.getChannelNumber        should be (1)
      queueD.getQueue                 should be (queueName.name)
      broker
    }

    program.run.unsafeRunSync()
  }

  it should "create an acker consumer and verify both envelope and ack result" in {
    import TestFs2Rabbit._

    val program = for {
      broker            <- EmbeddedAmqpBroker.createBroker
      channel           <- createConnectionChannel[IO]()
      testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
      ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
      _                 <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _                 <- declareQueue[IO](channel, queueName)
      _                 <- bindQueue[IO](channel, queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
      publisher         = createPublisher[IO](channel, exchangeName, routingKey)
      msg               = Stream(AmqpMessage("acker-test", AmqpProperties.empty))
      _                 <- msg.covary[IO] to publisher
      (acker, consumer) = createAckerConsumer[IO](channel, queueName)
      _                 <- Stream(
                            consumer to testQ.enqueue,
                            Stream(Ack(1)).covary[IO] observe ackerQ.enqueue to acker
                           ).join(2).take(1)
      result            <- Stream.eval(testQ.dequeue1)
      ackResult         <- Stream.eval(ackerQ.dequeue1)
    } yield {
      result    should be (AmqpEnvelope(1, "acker-test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      ackResult should be (Ack(1))
      broker
    }

    program.run.unsafeRunSync()
  }

  it should "NOT requeue a message in case of NAck when option 'requeueOnNack = false'" in {
    import TestFs2Rabbit._

    val program = for {
      broker            <- EmbeddedAmqpBroker.createBroker
      channel           <- createConnectionChannel[IO]()
      testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
      ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
      _                 <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _                 <- declareQueue[IO](channel, queueName)
      _                 <- bindQueue[IO](channel, queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
      publisher         = createPublisher[IO](channel, exchangeName, routingKey)
      msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
      _                 <- msg.covary[IO] to publisher
      (acker, consumer) = createAckerConsumer[IO](channel, queueName)
      _                 <- (consumer to testQ.enqueue).take(1)
      _                 <- (Stream(NAck(1)).covary[IO] observe ackerQ.enqueue to acker).take(1)
      result            <- Stream.eval(testQ.dequeue1)
      ackResult         <- Stream.eval(ackerQ.dequeue1)
    } yield {
      result    should be (AmqpEnvelope(1, "NAck-test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      ackResult should be (NAck(1))
      broker
    }

    program.run.unsafeRunSync()
  }

  it should "requeue a message in case of NAck when option 'requeueOnNack = true'" in {
    import TestNAckFs2Rabbit._

    val program = for {
      broker            <- EmbeddedAmqpBroker.createBroker
      channel           <- createConnectionChannel[IO]()
      testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
      ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
      _                 <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _                 <- declareQueue[IO](channel, queueName)
      _                 <- bindQueue[IO](channel, queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
      publisher         = createPublisher[IO](channel, exchangeName, routingKey)
      msg               = Stream(AmqpMessage("NAck-test", AmqpProperties.empty))
      _                 <- msg.covary[IO] to publisher
      (acker, consumer) = createAckerConsumer[IO](channel, queueName)
      _                 <- (consumer to testQ.enqueue).take(2) // Message will be requeued
      _                 <- (Stream(NAck(1)).covary[IO] observe ackerQ.enqueue to acker).take(1)
      result            <- testQ.dequeue.take(1)
      ackResult         <- ackerQ.dequeue.take(1)
    } yield {
      result    shouldBe an[AmqpEnvelope]
      ackResult should be (NAck(1))
      broker
    }

    program.run.unsafeRunSync()
  }

  it should "create a publisher, an auto-ack consumer, publish a message and consume it" in {
    import TestFs2Rabbit._

    val program = for {
      broker    <- EmbeddedAmqpBroker.createBroker
      channel   <- createConnectionChannel[IO]()
      testQ     <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
      _         <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _         <- declareQueue[IO](channel, queueName)
      _         <- bindQueue[IO](channel, queueName, exchangeName, routingKey)
      publisher = createPublisher[IO](channel, exchangeName, routingKey)
      consumer  = createAutoAckConsumer[IO](channel, queueName)
      msg       = Stream(AmqpMessage("test", AmqpProperties.empty))
      _         <- msg.covary[IO] to publisher
      _         <- (consumer to testQ.enqueue).take(1)
      result    <- Stream.eval(testQ.dequeue1)
    } yield {
      result should be (AmqpEnvelope(1, "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      broker
    }

    program.run.unsafeRunSync()
  }

  it should "create an exclusive auto-ack consumer with specific BasicQos" in {
    import TestFs2Rabbit._

    val program = for {
      broker        <- EmbeddedAmqpBroker.createBroker
      channel       <- createConnectionChannel[IO]()
      testQ         <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
      _             <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _             <- declareQueue[IO](channel, queueName)
      _             <- bindQueue[IO](channel, queueName, exchangeName, routingKey)
      publisher     = createPublisher[IO](channel, exchangeName, routingKey)
      consumerArgs  = ConsumerArgs(consumerTag = "XclusiveConsumer", noLocal = false, exclusive = true, args = Map.empty[String, AnyRef])
      consumer      = createAutoAckConsumer[IO](channel, queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
      msg           = Stream(AmqpMessage("test", AmqpProperties.empty))
      _             <- msg.covary[IO] to publisher
      _             <- (consumer to testQ.enqueue).take(1)
      result        <- Stream.eval(testQ.dequeue1)
    } yield {
      println(consumer)
      result should be (AmqpEnvelope(1, "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      broker
    }

    program.run.unsafeRunSync()
  }

  it should "create an exclusive acker consumer with specific BasicQos" in {
    import TestFs2Rabbit._

    val program = for {
      broker            <- EmbeddedAmqpBroker.createBroker
      channel           <- createConnectionChannel[IO]()
      testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
      ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
      _                 <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _                 <- declareQueue[IO](channel, queueName)
      _                 <- bindQueue[IO](channel, queueName, exchangeName, routingKey)
      publisher         = createPublisher[IO](channel, exchangeName, routingKey)
      consumerArgs      = ConsumerArgs(consumerTag = "XclusiveConsumer", noLocal = false, exclusive = true, args = Map.empty[String, AnyRef])
      (acker, consumer) = createAckerConsumer[IO](channel, queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
      msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
      _                 <- msg.covary[IO] to publisher
      _                 <- Stream(
                          consumer to testQ.enqueue,
                          Stream(Ack(1)).covary[IO] observe ackerQ.enqueue to acker
                        ).join(2).take(1)
      result            <- Stream.eval(testQ.dequeue1)
      ackResult         <- Stream.eval(ackerQ.dequeue1)
    } yield {
      result    should be (AmqpEnvelope(1, "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      ackResult should be (Ack(1))
      broker
    }

    program.run.unsafeRunSync()
  }

  it should "bind a queue with the nowait parameter set to true" in {
    import TestFs2Rabbit._

    val program = for {
      broker  <- EmbeddedAmqpBroker.createBroker
      channel <- createConnectionChannel[IO]()
      _       <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _       <- declareQueue[IO](channel, queueName)
      _       <- bindQueueNoWait[IO](channel, queueName, exchangeName, routingKey, QueueBindingArgs(Map.empty[String, AnyRef]))
    } yield broker

    program.run.unsafeRunSync()
  }

  it should "delete a queue" in {
    import TestFs2Rabbit._

    val program = for {
      broker  <- EmbeddedAmqpBroker.createBroker
      channel <- createConnectionChannel[IO]()
      _       <- declareExchange[IO](channel, exchangeName, ExchangeType.Direct)
      _       <- declareQueue[IO](channel, queueName)
      _       <- deleteQueue[IO](channel, queueName)
      either  <- createAutoAckConsumer[IO](channel, queueName).attempt
    } yield {
      either          shouldBe a[Left[_, _]]
      either.left.get shouldBe a[java.io.IOException]
      broker
    }

    program.run.unsafeRunSync()
  }

  ignore should "bind an exchange to another exhange" in {
    import TestFs2Rabbit._

    val sourceExchangeName = ExchangeName("sourceExchange")
    val destinationExchangeName = ExchangeName("destinationExchange")

    val program = for {
      broker            <- EmbeddedAmqpBroker.createBroker
      channel           <- createConnectionChannel[IO]()
      testQ             <- Stream.eval(async.boundedQueue[IO, AmqpEnvelope](100))
      ackerQ            <- Stream.eval(async.boundedQueue[IO, AckResult](100))
      _                 <- declareExchange[IO](channel, sourceExchangeName, ExchangeType.Direct)
      _                 <- declareExchange[IO](channel, destinationExchangeName, ExchangeType.Direct)
      _                 <- declareQueue[IO](channel, queueName)
      _                 <- bindQueue[IO](channel, queueName, destinationExchangeName, routingKey)
      _                 <- bindExchange[IO](channel, destinationExchangeName, sourceExchangeName, routingKey, ExchangeBindingArgs(Map.empty[String, AnyRef]))
      publisher         = createPublisher[IO](channel, sourceExchangeName, routingKey)
      consumerArgs      = ConsumerArgs(consumerTag = "XclusiveConsumer", noLocal = false, exclusive = true, args = Map.empty[String, AnyRef])
      (acker, consumer) = createAckerConsumer[IO](channel, queueName, BasicQos(prefetchSize = 0, prefetchCount = 10), Some(consumerArgs))
      msg               = Stream(AmqpMessage("test", AmqpProperties.empty))
      _                 <- msg.covary[IO] to publisher
      _                 <- Stream(
                          consumer to testQ.enqueue,
                          Stream(Ack(1)).covary[IO] observe ackerQ.enqueue to acker
                        ).join(2).take(1)
      result            <- Stream.eval(testQ.dequeue1)
      ackResult         <- Stream.eval(ackerQ.dequeue1)
    } yield {
      result    should be(AmqpEnvelope(1, "test", AmqpProperties(None, None, Map.empty[String, AmqpHeaderVal])))
      ackResult should be(Ack(1))
      broker
    }
  }

}
