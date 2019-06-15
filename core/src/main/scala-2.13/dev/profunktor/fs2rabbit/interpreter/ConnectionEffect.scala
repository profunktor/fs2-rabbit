/*
 * Copyright 2017-2019 ProfunKtor
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

package dev.profunktor.fs2rabbit.interpreter

import cats.data.NonEmptyList
import cats.effect.{Resource, Sync}
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.Connection
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.effects.Log
import dev.profunktor.fs2rabbit.model.{AMQPChannel, AMQPConnection, RabbitChannel, RabbitConnection}
import com.rabbitmq.client.{Address, ConnectionFactory}
import javax.net.ssl.SSLContext

import scala.jdk.CollectionConverters._

class ConnectionEffect[F[_]: Log: Sync](
                                         factory: ConnectionFactory,
                                         addresses: NonEmptyList[Address]
                                       ) extends Connection[Resource[F, ?]] {

  private[fs2rabbit] def acquireChannel(connection: AMQPConnection): F[AMQPChannel] =
    Sync[F]
      .delay(connection.value.createChannel)
      .flatTap(c => Log[F].info(s"Acquired channel: $c"))
      .map(RabbitChannel)

  private[fs2rabbit] val acquireConnection: F[AMQPConnection] =
    Sync[F]
      .delay(factory.newConnection(addresses.toList.asJava))
      .flatTap(c => Log[F].info(s"Acquired connection: $c"))
      .map(RabbitConnection)

  override def createConnection: Resource[F, AMQPConnection] =
    Resource.make(acquireConnection) {
      case RabbitConnection(conn) =>
        Log[F].info(s"Releasing connection: $conn previously acquired.") *>
          Sync[F].delay { if (conn.isOpen) conn.close() }
    }

  override def createChannel(connection: AMQPConnection): Resource[F, AMQPChannel] =
    Resource.make(acquireChannel(connection)) {
      case RabbitChannel(channel) =>
        Sync[F].delay { if (channel.isOpen) channel.close() }
    }
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
