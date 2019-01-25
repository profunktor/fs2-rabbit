package com.github.gvolpe.fs2rabbit.interpreter

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.{DockerRabbit, IOAssertion, model}
import fs2.Stream
import org.scalatest.{Matchers, WordSpec}

import scala.util.Random

class Fs2RabbitITSpec extends WordSpec with Matchers with DockerRabbit {

  "Fs2Rabbit" should {

    "consume published message" in IOAssertion {
      val queueName    = QueueName(randomString())
      val exchangeName = ExchangeName(randomString())
      val routingKey   = RoutingKey(randomString())

      def consumer(rabbit: Fs2Rabbit[IO]): Stream[IO, model.AmqpEnvelope[String]] =
        rabbit.createConnectionChannel.flatMap { implicit channel =>
          for {
            _        <- rabbit.declareQueue(DeclarationQueueConfig.default(queueName))
            _        <- rabbit.declareExchange(exchangeName, ExchangeType.Topic)
            _        <- rabbit.bindQueue(queueName, exchangeName, routingKey)
            consumer <- rabbit.createAutoAckConsumer[String](queueName)
            message  <- consumer
          } yield message
        }

      def producer(rabbit: Fs2Rabbit[IO], message: String): Stream[IO, Unit] =
        rabbit.createConnectionChannel.flatMap { implicit channel =>
          for {
            _         <- rabbit.declareQueue(DeclarationQueueConfig.default(queueName))
            _         <- rabbit.declareExchange(exchangeName, ExchangeType.Topic)
            _         <- rabbit.bindQueue(queueName, exchangeName, routingKey)
            publisher <- rabbit.createPublisher[String](exchangeName, routingKey)
            _         <- Stream(message).covary[IO].evalMap(publisher)
          } yield ()
        }

      val message = "Test text here"

      for {
        rabbit          <- Fs2Rabbit[IO](rabbitConfig)
        _               <- producer(rabbit, message).compile.drain
        receivedMessage <- consumer(rabbit).take(1).compile.lastOrError
      } yield {
        receivedMessage.exchangeName shouldBe exchangeName
        receivedMessage.routingKey shouldBe routingKey
        receivedMessage.payload shouldBe message
      }
    }

  }

  private def randomString(): String = Random.alphanumeric.take(6).mkString
}
