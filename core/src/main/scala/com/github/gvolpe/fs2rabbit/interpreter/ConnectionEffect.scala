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

import cats.data.NonEmptyList
import cats.effect.{Resource, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra.Connection
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.effects.Log
import com.github.gvolpe.fs2rabbit.model.{AMQPChannel, AMQPConnection, RabbitChannel, RabbitConnection}
import com.rabbitmq.client.{Address, ConnectionFactory, Connection => RabbitMQConnection}
import javax.net.ssl.SSLContext

import scala.collection.JavaConverters._

class ConnectionEffect[F[_]](factory: ConnectionFactory, addresses: NonEmptyList[Address])(
    implicit F: Sync[F],
    L: Log[F]
) extends Connection[Resource[F, ?]] {

  private[fs2rabbit] val acquireConnectionChannel: F[(RabbitMQConnection, AMQPChannel)] =
    for {
      conn    <- F.delay(factory.newConnection(addresses.toList.asJava))
      channel <- F.delay(conn.createChannel)
      _       <- L.info(s"Acquired connection: $conn")
    } yield (conn, RabbitChannel(channel))

  private[fs2rabbit] val acquireConnection: F[AMQPConnection] =
    for {
      conn <- F.delay(factory.newConnection(addresses.toList.asJava))
    } yield RabbitConnection(conn)

  override def createConnection: Resource[F, AMQPConnection] =
    Resource.make(acquireConnection) {
      case RabbitConnection(conn) =>
        L.info(s"Releasing connection: $conn previously acquired.") *>
          F.delay { if (conn.isOpen) conn.close() }
    }

  /**
    * Creates a connection and a channel guaranteeing clean up.
    **/
  override def createConnectionChannel: Resource[F, AMQPChannel] =
    Resource
      .make(
        acquireConnectionChannel
      ) {
        case (conn, RabbitChannel(channel)) =>
          L.info(s"Releasing connection: $conn previously acquired.") *>
            F.delay { if (channel.isOpen) channel.close() } *> F.delay { if (conn.isOpen) conn.close() }
        case (_, _) => F.raiseError[Unit](new Exception("Unreachable"))
      }
      .map { case (_, channel) => channel }
}

object ConnectionEffect {

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
