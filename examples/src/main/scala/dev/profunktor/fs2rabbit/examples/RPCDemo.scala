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
import java.util.UUID

import cats.data.{Kleisli, NonEmptyList}
import cats.effect._
import cats.implicits._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model._
import fs2.Stream

import scala.concurrent.duration.DurationInt

object RPCDemo extends IOApp.Simple {

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    nodes = NonEmptyList.one(
      Fs2RabbitNodeConfig(
        host = "127.0.0.1",
        port = 5672
      )
    ),
    username = Some("guest"),
    password = Some("guest"),
    ssl = false,
    connectionTimeout = 3.seconds,
    requeueOnNack = false,
    requeueOnReject = false,
    internalQueueSize = Some(500),
    requestedHeartbeat = 60.seconds,
    automaticRecovery = true,
    clientProvidedConnectionName = Some("app:rpc-demo")
  )

  def run: IO[Unit] =
    RabbitClient.default[IO](config).resource.use { implicit client =>
      val queue = QueueName("rpc_queue")
      runServer[IO](queue).concurrently(runClient[IO](queue)).compile.drain
    }

  def runServer[F[_]: Sync](rpcQueue: QueueName)(implicit R: RabbitClient[F]): Stream[F, Unit] =
    Stream.resource(R.createConnectionChannel).flatMap { implicit channel =>
      new RPCServer[F](rpcQueue).serve
    }

  def runClient[F[_]: Async](rpcQueue: QueueName)(implicit R: RabbitClient[F]): Stream[F, Unit] =
    Stream.resource(R.createConnectionChannel).flatMap { implicit channel =>
      val client = new RPCClient[F](rpcQueue)

      Stream(
        client.call("Message 1"),
        client.call("Message 2"),
        client.call("Message 3")
      ).parJoinUnbounded.void
    }

}

class RPCClient[F[_]: Sync](rpcQueue: QueueName)(implicit R: RabbitClient[F], channel: AMQPChannel) {

  private val EmptyExchange = ExchangeName("")

  implicit val encoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def call(body: String): Stream[F, AmqpEnvelope[String]] = {
    val correlationId = UUID.randomUUID().toString

    for {
      queue     <- Stream.eval(R.declareQueue)
      publisher <- Stream.eval(
                     R.createPublisher[AmqpMessage[String]](EmptyExchange, RoutingKey(rpcQueue.value))
                   )
      _         <- Stream.eval(putStrLn(s"[Client] Message $body. ReplyTo queue $queue. Correlation $correlationId"))
      message    = AmqpMessage(body, AmqpProperties(replyTo = Some(queue.value), correlationId = Some(correlationId)))
      _         <- Stream.eval(publisher(message))
      consumer  <- Stream.eval(R.createAutoAckConsumer(queue))
      response  <- consumer.filter(_.properties.correlationId.contains(correlationId)).take(1)
      _         <- Stream.eval(putStrLn(s"[Client] Request $body. Received response [${response.payload}]"))
    } yield response
  }

}

class RPCServer[F[_]: Sync](rpcQueue: QueueName)(implicit R: RabbitClient[F], channel: AMQPChannel) {

  private val EmptyExchange = ExchangeName("")

  private implicit val encoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def serve: Stream[F, Unit] =
    Stream
      .eval(
        R.declareQueue(DeclarationQueueConfig.default(rpcQueue)) *>
          R.createAutoAckConsumer(rpcQueue)
      )
      .flatMap(_.evalMap(handler))

  private def handler(e: AmqpEnvelope[String]): F[Unit] = {
    val correlationId = e.properties.correlationId
    val replyTo       = e.properties.replyTo.toRight(new IllegalArgumentException("ReplyTo parameter is missing"))

    for {
      rk        <- replyTo.liftTo[F]
      _         <- putStrLn(s"[Server] Received message [${e.payload}]. ReplyTo $rk. CorrelationId $correlationId")
      publisher <- R.createPublisher[AmqpMessage[String]](EmptyExchange, RoutingKey(rk))
      response   = AmqpMessage(s"Response for ${e.payload}", AmqpProperties(correlationId = correlationId))
      _         <- publisher(response)
    } yield ()

  }

}
