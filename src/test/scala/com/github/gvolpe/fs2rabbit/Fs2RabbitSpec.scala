package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.embedded.EmbeddedAmqpBroker
import com.github.gvolpe.fs2rabbit.model._
import fs2._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class Fs2RabbitSpec extends FlatSpecLike with Matchers with BeforeAndAfterAll {

  behavior of "Fs2Rabbit"

  override def beforeAll() = {
    EmbeddedAmqpBroker.start()
  }

  override def afterAll() = {
    EmbeddedAmqpBroker.shutdown()
  }

  object TestFs2Rabbit extends Fs2Rabbit with UnderlyingAmqpClient {
    override protected val log = LoggerFactory.getLogger(getClass)
    override protected val fs2RabbitConfig =
      Fs2RabbitConfig("localhost", 5672, "hostnameAlias", 3, requeueOnNack = false)
  }

  it should "create a connection, a channel, a queue and an exchange" in {
    import TestFs2Rabbit._

    val program = for {
      connAndChannel    <- createConnectionChannel[IO]()
      (conn, channel)   = connAndChannel
      queueD            <- declareQueue[IO](channel, "queueName")
      _                 <- declareExchange[IO](channel, "exName", ExchangeType.Topic)
    } yield {
      conn.toString             should be ("amqp://guest@127.0.0.1:5672/hostnameAlias")
      channel.getChannelNumber  should be (1)
      queueD.getQueue           should be ("queueName")
    }

    program.run.unsafeRunSync()
  }

  // TODO: Assert that consumer is consuming
  // For this we need to bind an exchange to a queue and create a publisher, yet not supported
  it should "create an auto-ack consumer" in {
    import TestFs2Rabbit._

    val testLogger = Fs2Utils.liftSink[IO, AmqpEnvelope]{ e => IO(println(e)) }

    val program = for {
      connAndChannel    <- createConnectionChannel[IO]()
      (_, channel)      = connAndChannel
      (acker, consumer) = createAckerConsumer[IO](channel, "daQ")
      _                 <- Stream(
                            consumer to testLogger,
                            Stream(Ack(1)).covary[IO] to acker
                           ).join(2).take(0)
    } yield ()

    program.run.unsafeRunSync()
  }

  // TODO: Assert that acker is receiving ack and consumer is consuming
  // For this we need to bind an exchange to a queue and create a publisher, yet not supported
  it should "create an acker-consumer" in {
    import TestFs2Rabbit._

    val testLogger = Fs2Utils.liftSink[IO, AmqpEnvelope]{ e => IO(println(e)) }

    val program = for {
      connAndChannel <- createConnectionChannel[IO]()
      (_, channel)   = connAndChannel
      consumer       = createAutoAckConsumer[IO](channel, "daQ")
      _              <- (consumer to testLogger).take(0)
    } yield ()

    program.run.unsafeRunSync()
  }

  // TODO: Assert that message is published
  // For this we need to bind an a queue to the "exName" exchange, yet not supported
  it should "create a publisher" in {
    import TestFs2Rabbit._

    val program = for {
      connAndChannel <- createConnectionChannel[IO]()
      (_, channel)   = connAndChannel
      publisher      = createPublisher[IO](channel, "exName", "rk")
      msg            = Stream(AmqpMessage("test", AmqpProperties.empty))
      _              <- msg.covary[IO] to publisher
    } yield ()

    program.run.unsafeRunSync()
  }

}
