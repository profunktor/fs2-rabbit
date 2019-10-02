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
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Sync}
import cats.implicits._
import com.rabbitmq.client.{Address, ConnectionFactory, DefaultSaslConfig, SaslConfig}
import dev.profunktor.fs2rabbit.algebra.Connection
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.effects.Log
import dev.profunktor.fs2rabbit.javaConversion._
import dev.profunktor.fs2rabbit.model.{AMQPChannel, AMQPConnection, RabbitChannel, RabbitConnection}
import javax.net.ssl.SSLContext

object ConnectionEffect {
  def apply[F[_]: ConcurrentEffect: ContextShift: Log](
      conf: Fs2RabbitConfig,
      sslCtx: Option[SSLContext] = None,
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConf: SaslConfig = DefaultSaslConfig.PLAIN
  ): Connection[Resource[F, ?]] = new ConnectionEffect[F] {
    override lazy val config: Fs2RabbitConfig        = conf
    override lazy val sslContext: Option[SSLContext] = sslCtx
    override lazy val saslConfig: SaslConfig         = saslConf
    override lazy val sync: Sync[F]                  = Sync[F]
    override lazy val log: Log[F]                    = Log[F]
  }

  private[fs2rabbit] def mkConnectionFactory[F[_]: Sync](
      config: Fs2RabbitConfig,
      sslContext: Option[SSLContext],
      saslConfig: SaslConfig
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
      factory.setSaslConfig(saslConfig)
      config.username.foreach(factory.setUsername)
      config.password.foreach(factory.setPassword)
      val addresses = config.nodes.map(node => new Address(node.host, node.port))
      (factory, addresses)
    }
}

trait ConnectionEffect[F[_]] extends Connection[Resource[F, ?]] {

  implicit val sync: Sync[F]
  implicit val log: Log[F]
  val config: Fs2RabbitConfig
  val sslContext: Option[SSLContext]
  val saslConfig: SaslConfig

  private[fs2rabbit] def acquireChannel(connection: AMQPConnection): F[AMQPChannel] =
    Sync[F]
      .delay(connection.value.createChannel)
      .flatTap(c => Log[F].info(s"Acquired channel: $c"))
      .map(RabbitChannel)

  private[fs2rabbit] val acquireConnection: F[AMQPConnection] =
    ConnectionEffect.mkConnectionFactory(config, sslContext, saslConfig).flatMap {
      case (factory, addresses) =>
        Sync[F]
          .delay(factory.newConnection(addresses.toList.asJava))
          .flatTap(c => Log[F].info(s"Acquired connection: $c"))
          .map(RabbitConnection)
    }

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
