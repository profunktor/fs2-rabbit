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
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.json.Fs2JsonEncoder
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{LongVal, StringVal}
import dev.profunktor.fs2rabbit.model._
import fs2.{Pipe, Pure, Stream}
import io.circe.Encoder

class AutoAckConsumerDemo[F[_]: Async](R: RabbitClient[F]) {
  private val queueName                                                     = QueueName("testQ")
  private val exchangeName                                                  = ExchangeName("testEX")
  private val routingKey                                                    = RoutingKey("testRK")
  implicit val stringMessageEncoder: MessageEncoder[F, AmqpMessage[String]] =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def logPipe: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =>
    putStrLn(s"Consumed: $amqpMsg").as(Ack(amqpMsg.deliveryTag))
  }

  val program: F[Unit] = R.createConnectionChannel.use { implicit channel =>
    for {
      _         <- R.declareQueue(DeclarationQueueConfig.default(queueName))
      _         <- R.declareExchange(exchangeName, ExchangeType.Topic)
      _         <- R.bindQueue(queueName, exchangeName, routingKey)
      publisher <- R.createPublisher[AmqpMessage[String]](exchangeName, routingKey)
      consumer  <- R.createAutoAckConsumer[String](queueName)
      _         <- new AutoAckFlow[F, String](consumer, logPipe, publisher).flow.compile.drain
    } yield ()
  }

}

class AutoAckFlow[F[_]: Async, A](
    consumer: Stream[F, AmqpEnvelope[A]],
    logger: Pipe[F, AmqpEnvelope[A], AckResult],
    publisher: AmqpMessage[String] => F[Unit]
) {

  import io.circe.generic.auto._

  case class Address(number: Int, streetName: String)
  case class Person(id: Long, name: String, address: Address)

  private def jsonEncoder = new Fs2JsonEncoder

  def encoderPipe[T: Encoder]: Pipe[F, AmqpMessage[T], AmqpMessage[String]] =
    _.map(jsonEncoder.jsonEncode[T])

  val jsonPipe: Pipe[Pure, AmqpMessage[Person], AmqpMessage[String]] = _.map(jsonEncoder.jsonEncode[Person])

  val simpleMessage =
    AmqpMessage("Hey!", AmqpProperties(headers = Map("demoId" -> LongVal(123), "app" -> StringVal("fs2RabbitDemo"))))
  val classMessage  = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

  val flow: Stream[F, Unit] =
    Stream(
      Stream(simpleMessage).covary[F].evalMap(publisher),
      Stream(classMessage).covary[F].through(encoderPipe[Person]).evalMap(publisher),
      consumer.through(logger).through(_.evalMap(putStrLn(_)))
    ).parJoin(3)

}
