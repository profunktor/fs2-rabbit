/*
 * Copyright 2017-2026 ProfunKtor
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

package dev.profunktor.fs2rabbit.otel4s

import cats.effect.Concurrent
import cats.syntax.flatMap.*
import dev.profunktor.fs2rabbit.model.{AmqpEnvelope, QueueName}
import dev.profunktor.fs2rabbit.otel4s.instances.*
import dev.profunktor.fs2rabbit.otel4s.internal.Semconv
import fs2.Stream
import org.typelevel.otel4s.trace.{SpanKind, Tracer}

trait TracedRabbitConsumer[F[_], A] {
  def queueName: QueueName
  def underlying: Stream[F, AmqpEnvelope[A]]

  final def records: Stream[F, AmqpEnvelope[A]] = underlying

  def process[B](envelope: AmqpEnvelope[A])(fa: F[B]): F[B]

  def recordsWithProcess[B](f: AmqpEnvelope[A] => F[B]): Stream[F, B] =
    underlying.evalMap(envelope => process(envelope)(f(envelope)))
}

object TracedRabbitConsumer {

  private[otel4s] def apply[F[_]: Concurrent: Tracer, A](
      queueName: QueueName,
      underlying: Stream[F, AmqpEnvelope[A]],
      config: RabbitTracer.Config
  ): TracedRabbitConsumer[F, A] =
    new Impl[F, A](queueName, underlying, config)

  private[otel4s] def noop[F[_], A](
      queueName: QueueName,
      underlying: Stream[F, AmqpEnvelope[A]]
  ): TracedRabbitConsumer[F, A] =
    new Noop[F, A](queueName, underlying)

  final private class Impl[F[_]: Concurrent: Tracer, A](
      override val queueName: QueueName,
      override val underlying: Stream[F, AmqpEnvelope[A]],
      config: RabbitTracer.Config
  ) extends TracedRabbitConsumer[F, A] {

    override def process[B](envelope: AmqpEnvelope[A])(fa: F[B]): F[B] = {
      val spanContext = Semconv.processSpanContext(queueName, envelope)
      val spanSetup   = config.processSpanSetup(spanContext)

      Tracer[F]
        .joinOrRoot(envelope.properties.headers)(Tracer[F].currentSpanContext)
        .flatMap { creationContext =>
          val builder = Tracer[F]
            .spanBuilder(spanSetup.spanName)
            .root
            .withSpanKind(SpanKind.Consumer)
            .withFinalizationStrategy(spanSetup.finalizationStrategy)
            .addAttributes(
              Semconv.processAttributes(spanContext, config.clientId) ++
                config.constAttributes ++
                spanSetup.attributes
            )

          creationContext
            .fold(builder)(context => builder.addLink(context, Semconv.processLinkAttributes(spanContext)))
            .build
            .surround(fa)
        }
    }
  }

  final private class Noop[F[_], A](
      override val queueName: QueueName,
      override val underlying: Stream[F, AmqpEnvelope[A]]
  ) extends TracedRabbitConsumer[F, A] {
    override def process[B](envelope: AmqpEnvelope[A])(fa: F[B]): F[B] = fa
  }
}
