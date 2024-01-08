/*
 * Copyright 2017-2024 ProfunKtor
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

import cats.effect.Resource
import cats.effect.Sync
import cats.effect.kernel.MonadCancel
import cats.implicits._
import cats.~>
import com.rabbitmq.client.Address
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultSaslConfig
import com.rabbitmq.client.MetricsCollector
import com.rabbitmq.client.SaslConfig
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.effects.Log
import dev.profunktor.fs2rabbit.model.AMQPChannel
import dev.profunktor.fs2rabbit.model.AMQPConnection
import dev.profunktor.fs2rabbit.model.RabbitChannel
import dev.profunktor.fs2rabbit.model.RabbitConnection

import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import java.util.concurrent.Executors

object ConnectionResource {
  type ConnectionResource[F[_]] = Connection[Resource[F, *]]

  @deprecated(message = "Use `make` with explicit ExecutionContext", since = "5.0.0")
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

    val numOfThreads            = Runtime.getRuntime().availableProcessors() * 2
    val esF: F[ExecutorService] = threadFactory
      .fold(Executors.newFixedThreadPool(numOfThreads).pure[F]) {
        _.map(Executors.newFixedThreadPool(numOfThreads, _))
      }
      .map { es =>
        sys.addShutdownHook(es.shutdown())
        es
      }

    for {
      es   <- esF
      fn   <- addThreadFactory
      conn <- _make(
                conf,
                Some(ExecutionContext.fromExecutorService(es)),
                sslCtx,
                saslConf,
                metricsCollector,
                fn
              )
    } yield conn
  }

  def make[F[_]: Sync: Log](
      conf: Fs2RabbitConfig,
      executionContext: ExecutionContext,
      sslCtx: Option[SSLContext],
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConf: SaslConfig,
      metricsCollector: Option[MetricsCollector],
      threadFactory: Option[F[ThreadFactory]]
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
        Some(executionContext),
        sslCtx,
        saslConf,
        metricsCollector,
        fn
      )
    }
  }

  private def _make[F[_]: Sync: Log](
      conf: Fs2RabbitConfig,
      executionContext: Option[ExecutionContext],
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
        executionContext.foreach(ec => factory.setSharedExecutor(fromExecutionContext(ec)))
        metricsCollector.foreach(factory.setMetricsCollector)
        addThreadFactory(factory)

        factory
      }
      .map { connectionFactory =>
        new Connection[Resource[F, *]] {

          private[fs2rabbit] val addresses = conf.nodes.map(node => new Address(node.host, node.port))

          private[fs2rabbit] val acquireConnection: F[AMQPConnection] =
            Sync[F]
              .delay(connectionFactory.newConnection(addresses.toList.asJava, conf.clientProvidedConnectionName.orNull))
              .flatTap(c => Log[F].info(s"Acquired connection: $c"))
              .map(RabbitConnection.apply)

          private[fs2rabbit] def acquireChannel(connection: AMQPConnection): F[AMQPChannel] =
            Sync[F]
              .delay(connection.value.createChannel)
              .flatTap(c => Log[F].info(s"Acquired channel: $c"))
              .map(RabbitChannel.apply)

          override def createConnection: Resource[F, AMQPConnection] =
            Resource.make(acquireConnection) { amqpConn =>
              Log[F].info(s"Releasing connection: ${amqpConn.value} previously acquired.") *>
                Sync[F].delay {
                  if (amqpConn.value.isOpen) amqpConn.value.close()
                }
            }

          override def createChannel(connection: AMQPConnection): Resource[F, AMQPChannel] =
            Resource.make(acquireChannel(connection)) { amqpChannel =>
              Sync[F].delay {
                if (amqpChannel.value.isOpen) amqpChannel.value.close()
              }
            }
        }
      }

  private def fromExecutionContext(ec: ExecutionContext): ExecutorService =
    ec match {
      case es: ExecutorService => es
      case _                   =>
        new AbstractExecutorService {
          override def isShutdown                                              = false
          override def isTerminated                                            = false
          override def shutdown()                                              = ()
          override def shutdownNow()                                           = Collections.emptyList[Runnable]
          override def execute(runnable: Runnable): Unit                       = ec execute runnable
          override def awaitTermination(length: Long, unit: TimeUnit): Boolean = false
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
