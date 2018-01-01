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

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.Fs2Utils.evalF
import com.github.gvolpe.fs2rabbit.algebra.ConnectionAlg
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.typeclasses.Log
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}
import fs2.Stream

class ConnectionProgram[F[_]](config: Fs2RabbitConfig)
                             (implicit F: Sync[F], L: Log[F]) extends ConnectionAlg[F, Stream[F, ?]] {

  private lazy val connFactory: F[ConnectionFactory] = F.delay {
    val factory = new ConnectionFactory()
    factory.setHost(config.host)
    factory.setPort(config.port)
    factory.setVirtualHost(config.virtualHost)
    factory.setConnectionTimeout(config.connectionTimeout)
    if (config.useSsl) {
      factory.useSslProtocol()
    }
    config.username.foreach(factory.setUsername)
    config.password.foreach(factory.setPassword)
    factory
  }

  override def acquireConnection: F[(Connection, Channel)] =
    for {
      factory <- connFactory
      conn    <- F.delay(factory.newConnection)
      channel <- F.delay(conn.createChannel)
    } yield (conn, channel)

  /**
    * Creates a connection and a channel in a safe way using Stream.bracket.
    * In case of failure, the resources will be cleaned up properly.
    **/
  override def createConnectionChannel: Stream[F, Channel] =
    Stream.bracket(acquireConnection)(
      cc => evalF[F, Channel](cc._2),
      cc => {
        val (conn, channel) = cc
        for {
          _ <- L.info(s"Releasing connection: $conn previously acquired.")
          _ <- F.delay { if (channel.isOpen) channel.close() }
          _ <- F.delay { if (conn.isOpen) conn.close() }
        } yield ()
      }
    )

}
