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

package com.github.gvolpe.fs2rabbit.examples

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

import cats.data.{Kleisli, NonEmptyList}
import cats.effect._
import cats.syntax.applicative._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import com.github.gvolpe.fs2rabbit.effects.MessageEncoder
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._
import fs2.Stream

object RPCDemo extends IOApp {

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
    connectionTimeout = 3,
    requeueOnNack = false,
    internalQueueSize = Some(500),
    automaticRecovery = true
  )

  override def run(args: List[String]): IO[ExitCode] =
    Fs2Rabbit[IO](config).flatMap { implicit fs2Rabbit =>
      val queue = QueueName("rpc_queue")
      runServer[IO](queue).concurrently(runClient[IO](queue)).compile.drain.as(ExitCode.Success)
    }

  def runServer[F[_]: Sync](rpcQueue: QueueName)(implicit R: Fs2Rabbit[F]): Stream[F, Unit] =
    R.createConnectionChannel.flatMap { implicit channel =>
      new RPCServer[F](rpcQueue).serve
    }

  def runClient[F[_]: Concurrent](rpcQueue: QueueName)(implicit R: Fs2Rabbit[F]): Stream[F, Unit] =
    R.createConnectionChannel.flatMap { implicit channel =>
      val client = new RPCClient[F](rpcQueue)

      Stream(
        client.call("Message 1"),
        client.call("Message 2"),
        client.call("Message 3")
      ).parJoinUnbounded.void
    }

}

class RPCClient[F[_]: Sync](rpcQueue: QueueName)(implicit R: Fs2Rabbit[F], channel: AMQPChannel) {

  private val EmptyExchange = ExchangeName("")

  implicit val encoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def call(body: String): Stream[F, AmqpEnvelope[String]] = {
    val correlationId = UUID.randomUUID().toString

    for {
      queue     <- R.declareQueue
      publisher <- R.createPublisher[AmqpMessage[String]](EmptyExchange, RoutingKey(rpcQueue.value))
      _         <- Stream.eval(putStrLn(s"[Client] Message $body. ReplyTo queue $queue. Correlation $correlationId"))
      message   = AmqpMessage(body, AmqpProperties(replyTo = Some(queue.value), correlationId = Some(correlationId)))
      _         <- Stream.eval(publisher(message))
      consumer  <- R.createAutoAckConsumer(queue)
      response  <- consumer.filter(_.properties.correlationId.contains(correlationId)).take(1)
      _         <- Stream.eval(putStrLn(s"[Client] Request $body. Received response [${response.payload}]"))
    } yield response
  }

}

class RPCServer[F[_]: Sync](rpcQueue: QueueName)(implicit R: Fs2Rabbit[F], channel: AMQPChannel) {

  private val EmptyExchange = ExchangeName("")

  private implicit val encoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def serve: Stream[F, Unit] =
    for {
      _        <- R.declareQueue(DeclarationQueueConfig.default(rpcQueue))
      consumer <- R.createAutoAckConsumer(rpcQueue)
      _        <- consumer.flatMap(handler)
    } yield ()

  private def handler(e: AmqpEnvelope[String]): Stream[F, Unit] = {
    val correlationId = e.properties.correlationId
    val replyTo       = e.properties.replyTo.toRight(new IllegalArgumentException("ReplyTo parameter is missing"))

    for {
      rk        <- Stream.fromEither[F](replyTo)
      _         <- Stream.eval(putStrLn(s"[Server] Received message [${e.payload}]. ReplyTo $rk. CorrelationId $correlationId"))
      publisher <- R.createPublisher[AmqpMessage[String]](EmptyExchange, RoutingKey(rk))
      response  = AmqpMessage(s"Response for ${e.payload}", AmqpProperties(correlationId = correlationId))
      _         <- Stream.eval(publisher(response))
    } yield ()

  }

}
