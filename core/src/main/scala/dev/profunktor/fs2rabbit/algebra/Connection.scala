/*
 * Copyright 2017-2020 ProfunKtor
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

package dev.profunktor.fs2rabbit.algebra

import java.util.concurrent.ThreadFactory

import cats.effect.kernel.MonadCancel
import cats.effect.{Resource, Sync}
import cats.implicits._
import cats.~>
import com.rabbitmq.client.{Address, ConnectionFactory, DefaultSaslConfig, MetricsCollector, SaslConfig}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.effects.Log
import dev.profunktor.fs2rabbit.javaConversion._
import dev.profunktor.fs2rabbit.model.{AMQPChannel, AMQPConnection, RabbitChannel, RabbitConnection}
import javax.net.ssl.SSLContext

object ConnectionResource {
  type ConnectionResource[F[_]] = Connection[Resource[F, *]]
  def make[F[_]: Sync: Log](
      conf: Fs2RabbitConfig,
      sslCtx: Option[SSLContext] = None,
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConf: SaslConfig = DefaultSaslConfig.PLAIN,
      metricsCollector: Option[MetricsCollector] = None,
      threadFactory: Option[F[ThreadFactory]] = None
  ): F[Connection[Resource[F, *]]] = {
    val addThreadFactory: F[ConnectionFactory => Unit] =
      threadFactory.fold(Sync[F].pure((_: ConnectionFactory) => ())) { threadFact =>
        threadFact.map { tf => (cf: ConnectionFactory) =>
          cf.setThreadFactory(tf)
        }
      }
    addThreadFactory.flatMap { fn =>
      _make(
        conf,
        sslCtx,
        saslConf,
        metricsCollector,
        fn
      )
    }
  }

  private def _make[F[_]: Sync: Log](
      conf: Fs2RabbitConfig,
      sslCtx: Option[SSLContext],
      saslConf: SaslConfig,
      metricsCollector: Option[MetricsCollector],
      addThreadFactory: ConnectionFactory => Unit
  ): F[Connection[Resource[F, *]]] =
    Sync[F]
      .delay {
        val factory   = new ConnectionFactory()
        val firstNode = conf.nodes.head
        factory.setHost(firstNode.host)
        factory.setPort(firstNode.port)
        factory.setVirtualHost(conf.virtualHost)
        factory.setConnectionTimeout(conf.connectionTimeout.toMillis.toInt)
        factory.setRequestedHeartbeat(conf.requestedHeartbeat.toSeconds.toInt)
        factory.setAutomaticRecoveryEnabled(conf.automaticRecovery)
        if (conf.ssl) sslCtx.fold(factory.useSslProtocol())(factory.useSslProtocol)
        factory.setSaslConfig(saslConf)
        conf.username.foreach(factory.setUsername)
        conf.password.foreach(factory.setPassword)
        metricsCollector.foreach(factory.setMetricsCollector)
        addThreadFactory(factory)
        factory
      }
      .map { connectionFactory =>
        new Connection[Resource[F, *]] {

          private[fs2rabbit] val addresses = conf.nodes.map(node => new Address(node.host, node.port))

          private[fs2rabbit] val acquireConnection: F[AMQPConnection] =
            Sync[F]
              .delay(connectionFactory.newConnection(addresses.toList.asJava))
              .flatTap(c => Log[F].info(s"Acquired connection: $c"))
              .map(RabbitConnection)

          private[fs2rabbit] def acquireChannel(connection: AMQPConnection): F[AMQPChannel] =
            Sync[F]
              .delay(connection.value.createChannel)
              .flatTap(c => Log[F].info(s"Acquired channel: $c"))
              .map(RabbitChannel)

          override def createConnection: Resource[F, AMQPConnection] =
            Resource.make(acquireConnection) {
              case RabbitConnection(conn) =>
                Log[F].info(s"Releasing connection: $conn previously acquired.") *>
                  Sync[F].delay {
                    if (conn.isOpen) conn.close()
                  }
            }

          override def createChannel(connection: AMQPConnection): Resource[F, AMQPChannel] =
            Resource.make(acquireChannel(connection)) {
              case RabbitChannel(channel) =>
                Sync[F].delay {
                  if (channel.isOpen) channel.close()
                }
            }
        }
      }

  implicit class ConnectionResourceOps[F[_]](val cf: ConnectionResource[F]) extends AnyVal {
    def mapK[G[_]](fk: F ~> G)(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): ConnectionResource[G] =
      new Connection[Resource[G, *]] {
        def createConnection: Resource[G, AMQPConnection]                       = cf.createConnection.mapK(fk)
        def createChannel(connection: AMQPConnection): Resource[G, AMQPChannel] = cf.createChannel(connection).mapK(fk)
      }
  }
}

trait Connection[F[_]] {
  def createConnection: F[AMQPConnection]
  def createChannel(connection: AMQPConnection): F[AMQPChannel]
}
