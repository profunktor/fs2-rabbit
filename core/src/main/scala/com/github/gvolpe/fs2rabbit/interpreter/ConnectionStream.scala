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

package com.github.gvolpe.fs2rabbit.interpreter

import javax.net.ssl.SSLContext
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra.Connection
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model.{AMQPChannel, RabbitChannel}
import com.github.gvolpe.fs2rabbit.effects.Log
import com.rabbitmq.client.{Address, ConnectionFactory, Connection => RabbitMQConnection}
import fs2.Stream
import scala.collection.JavaConverters._

class ConnectionStream[F[_]](
    factory: ConnectionFactory,
    addresses: NonEmptyList[Address]
)(implicit F: Sync[F], L: Log[F])
    extends Connection[Stream[F, ?]] {

  private[fs2rabbit] val acquireConnection: F[(RabbitMQConnection, AMQPChannel)] =
    for {
      conn    <- F.delay(factory.newConnection(addresses.toList.asJava))
      channel <- F.delay(conn.createChannel)
    } yield (conn, RabbitChannel(channel))

  /**
    * Creates a connection and a channel in a safe way using Stream.bracket.
    * In case of failure, the resources will be cleaned up properly.
    **/
  override def createConnectionChannel: Stream[F, AMQPChannel] =
    Stream
      .bracket(acquireConnection) {
        case (conn, RabbitChannel(channel)) =>
          L.info(s"Releasing connection: $conn previously acquired.") *>
            F.delay { if (channel.isOpen) channel.close() } *> F.delay { if (conn.isOpen) conn.close() }
        case (_, _) => F.raiseError[Unit](new Exception("Unreachable"))
      }
      .map { case (_, channel) => channel }

}

object ConnectionStream {

  private[fs2rabbit] def mkConnectionFactory[F[_]: Sync](
      config: Fs2RabbitConfig,
      sslContext: Option[SSLContext]
  ): F[(ConnectionFactory, NonEmptyList[Address])] =
    Sync[F].delay {
      val factory   = new ConnectionFactory()
      val firstNode = config.nodes.head
      factory.setHost(firstNode.host)
      factory.setPort(firstNode.port)
      factory.setVirtualHost(config.virtualHost)
      factory.setConnectionTimeout(config.connectionTimeout)
      factory.setAutomaticRecoveryEnabled(config.automaticRecovery)
      if (config.ssl) {
        sslContext.fold(factory.useSslProtocol())(factory.useSslProtocol)
      }
      config.username.foreach(factory.setUsername)
      config.password.foreach(factory.setPassword)
      val addresses = config.nodes.map(node => new Address(node.host, node.port))
      (factory, addresses)
    }

}
