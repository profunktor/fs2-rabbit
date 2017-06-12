package com.github.gvolpe.fs2rabbit

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory, Consumer}
import fs2._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class Fs2RabbitSpec extends FlatSpecLike with Matchers with MockitoSugar {

  behavior of "Fs2Rabbit"

  object MockFs2Rabbit extends Fs2Rabbit {
    protected override val factory = mock[ConnectionFactory]
    protected override val fs2RabbitConfig = Fs2RabbitConfig("localhost", 5672, 3, requeueOnNack = false)

    val mockChannel     = mock[Channel]
    val mockConnection  = mock[Connection]
    val mockExchangeD   = mock[Exchange.DeclareOk]
    val mockQueueD      = mock[Queue.DeclareOk]

    when(factory.newConnection).thenReturn(mockConnection)
    when(mockConnection.createChannel()).thenReturn(mockChannel)
    when(mockConnection.isOpen).thenReturn(true)
    when(mockChannel.isOpen).thenReturn(true)
    when(mockChannel.basicConsume(anyString(), anyBoolean(), any(classOf[Consumer]))).thenReturn("MockConsumer")
    when(mockChannel.exchangeDeclare(anyString(), anyString())).thenReturn(mockExchangeD)
    when(mockChannel.queueDeclare(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), any[java.util.Map[String, Object]])).thenReturn(mockQueueD)
  }

  it should "create a connection, a channel, a queue and an exchange" in {
    import MockFs2Rabbit._

    val program = for {
      connAndChannel    <- createConnectionChannel()
      (conn, channel)   = connAndChannel
      queueD            <- declareQueue(channel, "queueName")
      exD               <- declareExchange(channel, "exName", ExchangeType.Topic)
    } yield {
      conn      should be (mockConnection)
      channel   should be (mockChannel)
      queueD    should be (mockQueueD)
      exD       should be (mockExchangeD)
    }

    program.run.unsafeRunSync()
  }

  // Probably doesn't even make sense to have this test
  it should "create an auto-ack consumer" in {
    import MockFs2Rabbit._

    val testLogger = Fs2Utils.liftSink[AmqpEnvelope]{ e => IO(println(e)) }

    val program = for {
      connAndChannel    <- createConnectionChannel()
      (_, channel)      = connAndChannel
      (acker, consumer) = createAckerConsumer(channel, "daQ")
      _                 <- Stream(
                            consumer to testLogger,
                            Stream(Ack(1)).covary[IO] to acker
                           ).join(2).take(0)
    } yield ()

    program.run.unsafeRunSync()
  }

  // Probably doesn't even make sense to have this test
  it should "create an acker-consumer" in {
    import MockFs2Rabbit._

    val testLogger = Fs2Utils.liftSink[AmqpEnvelope]{ e => IO(println(e)) }

    val program = for {
      connAndChannel <- createConnectionChannel()
      (_, channel)   = connAndChannel
      consumer       = createAutoAckConsumer(channel, "daQ")
      _              <- (consumer to testLogger).take(0)
    } yield ()

    program.run.unsafeRunSync()
  }

  it should "create a publisher" in {
    import MockFs2Rabbit._

    val program = for {
      connAndChannel <- createConnectionChannel()
      (_, channel)   = connAndChannel
      publisher      = createPublisher(channel, "exName", "rk")
      msg            = Stream(AmqpMessage("test", AmqpProperties.empty))
      _              <- msg.covary[IO] to publisher
    } yield ()

    program.run.unsafeRunSync()
  }

}
