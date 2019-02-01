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

import cats.data.Kleisli
import cats.effect.{Concurrent, Timer}
import cats.syntax.applicative._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder
import com.github.gvolpe.fs2rabbit.model.AckResult.Ack
import com.github.gvolpe.fs2rabbit.model.AmqpHeaderVal.{LongVal, StringVal}
import com.github.gvolpe.fs2rabbit.model._
import fs2.{Pipe, Stream}

class AckerConsumerDemo[F[_]: Concurrent: Timer](implicit R: Fs2Rabbit[F]) {

  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")
  implicit val stringMessageEncoder =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def logPipe: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =>
    putStrLn(s"Consumed: $amqpMsg").as(Ack(amqpMsg.deliveryTag))
  }

  val publishingFlag: PublishingFlag = PublishingFlag(mandatory = true)

  // Run when there's no consumer for the routing key specified by the publisher and the flag mandatory is true
  val publishingListener: PublishReturn => F[Unit] = pr => putStrLn(s"Publish listener: $pr")

  val program: Stream[F, Unit] = R.createConnectionChannel.flatMap { implicit channel =>
    for {
      _                 <- R.declareQueue(DeclarationQueueConfig.default(queueName))
      _                 <- R.declareExchange(exchangeName, ExchangeType.Topic)
      _                 <- R.bindQueue(queueName, exchangeName, routingKey)
      (acker, consumer) <- R.createAckerConsumer[String](queueName)
      publisher <- R.createPublisherWithListener[AmqpMessage[String]](
                    exchangeName,
                    routingKey,
                    publishingFlag,
                    publishingListener
                  )
      result <- new Flow[F, String](consumer, acker, logPipe, publisher).flow
    } yield result
  }

}

class Flow[F[_]: Concurrent, A](
    consumer: Stream[F, AmqpEnvelope[A]],
    acker: AckResult => F[Unit],
    logger: Pipe[F, AmqpEnvelope[A], AckResult],
    publisher: AmqpMessage[String] => F[Unit]
) {

  import io.circe.generic.auto._

  case class Address(number: Int, streetName: String)
  case class Person(id: Long, name: String, address: Address)

  private val jsonEncoder = new Fs2JsonEncoder[F]
  import jsonEncoder.jsonEncode

  val simpleMessage =
    AmqpMessage("Hey!", AmqpProperties(headers = Map("demoId" -> LongVal(123), "app" -> StringVal("fs2RabbitDemo"))))
  val classMessage = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

  val flow: Stream[F, Unit] =
    Stream(
      Stream(simpleMessage).covary[F].evalMap(publisher),
      Stream(classMessage).covary[F].through(jsonEncode[Person]).evalMap(publisher),
      consumer.through(logger).evalMap(acker)
    ).parJoin(3)

}
