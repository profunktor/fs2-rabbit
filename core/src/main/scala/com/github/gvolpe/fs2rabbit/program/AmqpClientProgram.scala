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

package com.github.gvolpe.fs2rabbit.program

import java.util.concurrent.Executors

import cats.effect.{Async, IO, Sync}
import com.github.gvolpe.fs2rabbit.Fs2Utils.{evalF, liftSink}
import com.github.gvolpe.fs2rabbit.algebra.AmqpClientAlg
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model.{Ack, AckResult, AmqpEnvelope, AmqpProperties, BasicQos, DeliveryTag, NAck, QueueName, StreamConsumer}
import com.rabbitmq.client._
import fs2.async.mutable
import fs2.{Pipe, Sink, Stream}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class AmqpClientProgram[F[_]](config: Fs2RabbitConfig)
                             (implicit F: Async[F]) extends AmqpClientAlg[Stream[F, ?], Sink[F, ?]] {

  private def defaultConsumer(channel: Channel): (mutable.Queue[IO, Either[Throwable, AmqpEnvelope]], Consumer) = {
    implicit val queueEC: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

    val daQ = fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](100).unsafeRunSync()

    val consumer = new DefaultConsumer(channel) {

      override def handleCancel(consumerTag: String): Unit = {
        daQ.enqueue1(Left(new Exception(s"Queue might have been DELETED! $consumerTag"))).unsafeRunSync()
      }

      override def handleDelivery(consumerTag: String,
                                  envelope: Envelope,
                                  properties: AMQP.BasicProperties,
                                  body: Array[Byte]): Unit = {
        val msg   = new String(body, "UTF-8")
        val tag   = envelope.getDeliveryTag
        val props = AmqpProperties.from(properties)
        daQ.enqueue1(Right(AmqpEnvelope(new DeliveryTag(tag), msg, props))).unsafeRunSync()
      }

    }

    (daQ, consumer)
  }

  private def resilientConsumer: Pipe[F, Either[Throwable, AmqpEnvelope], AmqpEnvelope] =
    _.flatMap {
      case Left(err)  => Stream.raiseError(err)
      case Right(env) => evalF[F, AmqpEnvelope](env)
    }

  override def createAcker(channel: Channel): Sink[F, AckResult] =
    liftSink[F, AckResult] {
      case Ack(tag)   => Sync[F].delay(channel.basicAck(tag.value, false))
      case NAck(tag)  => Sync[F].delay(channel.basicNack(tag.value, false, config.requeueOnNack))
    }

  override def createConsumer(queueName: QueueName,
                              channel: Channel,
                              basicQos: BasicQos,
                              autoAck: Boolean = false,
                              noLocal: Boolean = false,
                              exclusive: Boolean = false,
                              consumerTag: String = "",
                              args: Map[String, AnyRef] = Map.empty[String, AnyRef]): StreamConsumer[F] = {
    val (daQ, dc) = defaultConsumer(channel)
    for {
      _         <- evalF[F, Unit](channel.basicQos(basicQos.prefetchSize, basicQos.prefetchCount, basicQos.global))
      _         <- evalF[F, String](channel.basicConsume(queueName.value, autoAck, consumerTag, noLocal, exclusive, args.asJava, dc))
      consumer  <- Stream.repeatEval(daQ.dequeue1.to[F]) through resilientConsumer
    } yield consumer
  }

}
