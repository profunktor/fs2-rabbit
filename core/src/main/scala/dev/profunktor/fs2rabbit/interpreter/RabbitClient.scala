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

import cats.effect._
import cats.{Applicative, Apply, Monad}
import com.rabbitmq.client.{DefaultSaslConfig, SaslConfig}
import dev.profunktor.fs2rabbit.algebra.AckConsumingStream.AckConsumingStream
import dev.profunktor.fs2rabbit.algebra.ConnectionResource.ConnectionResource
import dev.profunktor.fs2rabbit.algebra.ConsumingStream.ConsumingStream
import dev.profunktor.fs2rabbit.algebra.{Acking, Binding, InternalQueue, _}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.effects.Log
import dev.profunktor.fs2rabbit.program.{
  AckConsumingProgramOld,
  AckingProgramOld,
  ConsumingProgramOld,
  PublishingProgramOld
}
import javax.net.ssl.SSLContext

object RabbitClient {
  def apply[F[_]: ConcurrentEffect: ContextShift](
      configuration: Fs2RabbitConfig,
      block: Blocker,
      sslCtx: Option[SSLContext] = None,
      saslCfg: SaslConfig = DefaultSaslConfig.PLAIN
  ): RabbitClient[F] =
    new RabbitClient[F] with ConnectionEffect[F] with ConsumeEffect[F] with BindingEffect[F] with PublishEffect[F]
    with DeclarationEffect[F] with DeletionEffect[F] with ConsumingProgramOld[F] with PublishingProgramOld[F]
    with AckingProgramOld[F] with AckConsumingProgramOld[F] {
      override lazy val contextShift: ContextShift[F]  = ContextShift[F]
      override lazy val effect: Effect[F]              = Effect[F]
      override lazy val sync: Sync[F]                  = effect
      override lazy val bracket: Bracket[F, Throwable] = sync
      override lazy val monad: Monad[F]                = effect
      override lazy val applicative: Applicative[F]    = monad
      override lazy val apply: Apply[F]                = monad
      override lazy val IQ: InternalQueue[F]           = new LiveInternalQueue[F](configuration.internalQueueSize.getOrElse(500))
      override lazy val log: Log[F]                    = Log[F]
      override lazy val blocker: Blocker               = block
      override lazy val config: Fs2RabbitConfig        = configuration
      override lazy val sslContext: Option[SSLContext] = sslCtx
      override lazy val saslConfig: SaslConfig         = saslCfg
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
