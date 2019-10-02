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

package dev.profunktor.fs2rabbit.algebra

import dev.profunktor.fs2rabbit.algebra.AckConsumingStream.AckConsumingStream
import dev.profunktor.fs2rabbit.algebra.ConsumingStream.ConsumingStream
import dev.profunktor.fs2rabbit.algebra.ConnectionResource.ConnectionResource
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter._
import dev.profunktor.fs2rabbit.program.{AckConsumingProgram, AckingProgram, ConsumingProgram, PublishingProgram}
import cats.Monad
import cats.Apply
import cats.Applicative
import cats.effect._
import dev.profunktor.fs2rabbit.effects.Log
import com.rabbitmq.client.SaslConfig
import javax.net.ssl.SSLContext
import com.rabbitmq.client.{DefaultSaslConfig, SaslConfig}

object RabbitClient {
  def apply[F[_]: ConcurrentEffect: ContextShift](
      configuration: Fs2RabbitConfig,
      block: Blocker,
      sslCtx: Option[SSLContext] = None,
      saslCfg: SaslConfig = DefaultSaslConfig.PLAIN
  ): RabbitClient[F] =
    new RabbitClient[F] with ConnectionEffect[F] with ConsumeEffect[F] with BindingEffect[F] with PublishEffect[F]
    with DeclarationEffect[F] with DeletionEffect[F] with ConsumingProgram[F] with PublishingProgram[F]
    with AckingProgram[F] with AckConsumingProgram[F] {
      override val blocker: Blocker               = block
      override val contextShift: ContextShift[F]  = ContextShift[F]
      override val effectF: Effect[F]             = Effect[F]
      override val m: Monad[F]                    = Monad[F]
      override val apply: Apply[F]                = m
      override val bracket: Bracket[F, Throwable] = Bracket[F, Throwable]
      override val IQ: InternalQueue[F]           = new LiveInternalQueue[F](configuration.internalQueueSize.getOrElse(500))
      override val config                         = configuration
      override val applicative: Applicative[F]    = m

      override val sync: Sync[F]                  = effectF
      override val log: Log[F]                    = Log[F]
      override val sslContext: Option[SSLContext] = sslCtx
      override val saslConfig: SaslConfig         = saslCfg
    }
}

trait RabbitClient[F[_]]
    extends ConnectionResource[F]
    with AckConsumingStream[F]
    with Acking[F]
    with Binding[F]
    with Consume[F]
    with ConsumingStream[F]
    with Declaration[F]
    with Deletion[F]
    with Publish[F]
    with Publishing[F]
