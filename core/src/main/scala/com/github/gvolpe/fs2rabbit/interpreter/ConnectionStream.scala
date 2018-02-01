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

package com.github.gvolpe.fs2rabbit.interpreter

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra.Connection
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model.{AMQPChannel, RabbitChannel}
import com.github.gvolpe.fs2rabbit.typeclasses.{Log, StreamEval}
import com.rabbitmq.client.{ConnectionFactory, Connection => RabbitMQConnection}
import fs2.Stream

class ConnectionStream[F[_]](config: F[Fs2RabbitConfig])(implicit F: Sync[F], L: Log[F], SE: StreamEval[F])
    extends Connection[F, Stream[F, ?]] {

  private lazy val connFactory: F[ConnectionFactory] =
    config.map { c =>
      val factory = new ConnectionFactory()
      factory.setHost(c.host)
      factory.setPort(c.port)
      factory.setVirtualHost(c.virtualHost)
      factory.setConnectionTimeout(c.connectionTimeout)
      if (c.useSsl) {
        factory.useSslProtocol()
      }
      c.username.foreach(factory.setUsername)
      c.password.foreach(factory.setPassword)
      factory
    }

  private def acquireConnection: F[(RabbitMQConnection, AMQPChannel)] =
    for {
      factory <- connFactory
      conn    <- F.delay(factory.newConnection)
      channel <- F.delay(conn.createChannel)
    } yield (conn, RabbitChannel(channel))

  /**
    * Creates a connection and a channel in a safe way using Stream.bracket.
    * In case of failure, the resources will be cleaned up properly.
    **/
  override def createConnectionChannel: Stream[F, AMQPChannel] =
    Stream.bracket(acquireConnection)(
      { case (_, channel) => SE.evalF[AMQPChannel](channel) }, {
        case (conn, RabbitChannel(channel)) =>
          for {
            _ <- L.info(s"Releasing connection: $conn previously acquired.")
            _ <- F.delay { if (channel.isOpen) channel.close() }
            _ <- F.delay { if (conn.isOpen) conn.close() }
          } yield ()
        case (_, _) => F.raiseError[Unit](new Exception("Unreacheable"))
      }
    )

}
