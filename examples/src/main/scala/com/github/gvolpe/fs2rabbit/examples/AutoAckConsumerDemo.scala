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

package com.github.gvolpe.fs2rabbit.examples

import cats.effect.{Concurrent, Sync}
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder
import com.github.gvolpe.fs2rabbit.model.AckResult.Ack
import com.github.gvolpe.fs2rabbit.model.AmqpHeaderVal.{LongVal, StringVal}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.effects.StreamEval
import fs2.{Pipe, Stream}

class AutoAckConsumerDemo[F[_]: Concurrent, A](implicit R: Fs2Rabbit[F, A]) {

  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")

  def putStrLn(str: String): F[Unit] = Sync[F].delay(println(str))

  def logPipe: Pipe[F, AmqpEnvelope[A], AckResult] = _.evalMap { amqpMsg =>
    putStrLn(s"Consumed: $amqpMsg").as(Ack(amqpMsg.deliveryTag))
  }

  val program: Stream[F, Unit] = R.createConnectionChannel.flatMap { implicit channel =>
    for {
      _         <- R.declareQueue(DeclarationQueueConfig.default(queueName))
      _         <- R.declareExchange(exchangeName, ExchangeType.Topic)
      _         <- R.bindQueue(queueName, exchangeName, routingKey)
      consumer  <- R.createAutoAckConsumer(queueName)
      publisher <- R.createPublisher(exchangeName, routingKey)
      result    <- new AutoAckFlow(consumer, logPipe, publisher).flow
    } yield result
  }

}

class AutoAckFlow[F[_]: Concurrent, A](
    consumer: StreamConsumer[F, A],
    logger: Pipe[F, AmqpEnvelope[A], AckResult],
    publisher: StreamPublisher[F]
)(implicit SE: StreamEval[F]) {

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
      Stream(simpleMessage).covary[F] to publisher,
      Stream(classMessage).covary[F] through jsonEncode[Person] to publisher,
      consumer through logger to SE.liftSink(ack => Sync[F].delay(println(ack)))
    ).parJoin(3)

}
