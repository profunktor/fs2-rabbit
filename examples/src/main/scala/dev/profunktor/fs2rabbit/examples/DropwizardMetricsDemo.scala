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

package dev.profunktor.fs2rabbit.examples

import java.nio.charset.StandardCharsets.UTF_8
import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{IO, IOApp, Resource, Sync}
import cats.implicits._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
import com.rabbitmq.client.impl.StandardMetricsCollector
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model.ExchangeType.Topic
import dev.profunktor.fs2rabbit.model._
import fs2._

import scala.concurrent.duration._

object DropwizardMetricsDemo extends IOApp.Simple {

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    nodes = NonEmptyList.one(Fs2RabbitNodeConfig(host = "127.0.0.1", port = 5672)),
    username = Some("guest"),
    password = Some("guest"),
    ssl = false,
    connectionTimeout = 3.seconds,
    requeueOnNack = false,
    requeueOnReject = false,
    internalQueueSize = Some(500),
    requestedHeartbeat = 60.seconds,
    automaticRecovery = true,
    clientProvidedConnectionName = Some("app:drop-wizard-metrics-demo")
  )

  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")

  val simpleMessage = AmqpMessage("Hey!", AmqpProperties.empty)

  implicit val stringMessageEncoder: MessageEncoder[IO, AmqpMessage[String]] =
    Kleisli[IO, AmqpMessage[String], AmqpMessage[Array[Byte]]] { s =>
      s.copy(payload = s.payload.getBytes(UTF_8)).pure[IO]
    }

  override def run: IO[Unit] = {
    val registry            = new MetricRegistry
    val dropwizardCollector = new StandardMetricsCollector(registry)

    val resources = for {
      _       <- JmxReporterResource.make[IO](registry)
      client  <- RabbitClient.default[IO](config).withMetricsCollector(dropwizardCollector).resource
      channel <- client.createConnection.flatMap(client.createChannel)
    } yield (channel, client)

    val program = resources.use { case (channel, client) =>
      implicit val c = channel

      val setup =
        for {
          _                <- client.declareQueue(DeclarationQueueConfig.default(queueName))
          _                <- client.declareExchange(DeclarationExchangeConfig.default(exchangeName, Topic))
          _                <- client.bindQueue(queueName, exchangeName, routingKey)
          ackerConsumer    <- client.createAckerConsumer[String](queueName)
          (acker, consumer) = ackerConsumer
          publisher        <- client.createPublisher[AmqpMessage[String]](exchangeName, routingKey)
        } yield (consumer, acker, publisher)

      Stream
        .eval(setup)
        .flatTap { case (consumer, acker, publisher) =>
          Stream(
            Stream(simpleMessage).evalMap(publisher).repeat.metered(1.second),
            consumer.through(logPipe).evalMap(acker)
          ).parJoin(2)
        }
        .compile
        .drain
    }

    program
  }

  def logPipe[F[_]: Sync]: Pipe[F, AmqpEnvelope[String], AckResult] =
    _.evalMap { amqpMsg =>
      putStrLn(s"Consumed: $amqpMsg").as(Ack(amqpMsg.deliveryTag))
    }

}

object JmxReporterResource {
  def make[F[_]: Sync](registry: MetricRegistry): Resource[F, JmxReporter] = {
    val acquire = Sync[F].delay {
      val reporter = JmxReporter.forRegistry(registry).inDomain("com.rabbitmq.client.jmx").build
      reporter.start()
      reporter
    }

    val close = (reporter: JmxReporter) => Sync[F].delay(reporter.close()).void

    Resource.make(acquire)(close)
  }
}
