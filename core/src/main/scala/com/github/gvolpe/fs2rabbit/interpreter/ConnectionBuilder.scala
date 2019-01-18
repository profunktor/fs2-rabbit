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

package com.github.gvolpe.fs2rabbit.interpreter

import cats.data.NonEmptyList
import cats.effect.{Resource, Sync}
import cats.syntax.apply._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra.Connection
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.effects.Log
import com.github.gvolpe.fs2rabbit.model.{AMQPChannel, RabbitChannel}
import com.rabbitmq.client.{Address, ConnectionFactory}
import javax.net.ssl.SSLContext

import scala.collection.JavaConverters._

class ConnectionBuilder[F[_]](
    factory: ConnectionFactory,
    addresses: NonEmptyList[Address]
)(implicit F: Sync[F], L: Log[F])
    extends Connection[F] {

  override def createConnectionChannel: Resource[F, AMQPChannel] =
    for {
      conn <- Resource.make(F.delay(factory.newConnection(addresses.toList.asJava))) { c =>
               L.info(s"Releasing connection: $c previously acquired.") *> F.delay { if (c.isOpen) c.close() }
             }
      channel <- Resource.fromAutoCloseable(F.delay(conn.createChannel))
      _       <- Resource.liftF(L.info(s"Acquired connection: $conn"))
    } yield RabbitChannel(channel)

}

object ConnectionBuilder {

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
