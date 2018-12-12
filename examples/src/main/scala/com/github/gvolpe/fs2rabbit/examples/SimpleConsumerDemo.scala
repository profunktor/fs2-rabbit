/*
 * Copyright 2017-2019 Fs2 Rabbit
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

import cats.effect.Concurrent
import cats.implicits._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.model._

class SimpleConsumerDemo[F[_]: Concurrent](implicit R: Fs2Rabbit[F]) {
  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")

  val program: F[Unit] = R.createConnectionChannel use { implicit channel =>
    for {
      _        <- R.declareQueue(DeclarationQueueConfig.default(queueName))
      _        <- R.declareExchange(exchangeName, ExchangeType.Topic)
      _        <- R.bindQueue(queueName, exchangeName, routingKey)
      consumer <- R.createAutoAckConsumer[String](queueName)
      p        <- (new SimpleConsumerProgram[F](consumer)).run
    } yield p
  }
}

class SimpleConsumerProgram[F[_]: Concurrent](consume: F[AmqpEnvelope[String]]) {
  private val log: AmqpEnvelope[String] => F[Unit] = amqpMsg => putStrLn(s"Consumed: $amqpMsg")

  /*
   * Fetch and log three messages, then exit
   * */
  val run: F[Unit] = for {
    msg1 <- consume
    _    <- log(msg1)
    msg2 <- consume
    _    <- log(msg2)
    msg3 <- consume
    _    <- log(msg3)
  } yield ()
}
