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

import cats.effect.{Concurrent, Resource}
import cats.syntax.functor.*
import cats.syntax.semigroup.*
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import org.typelevel.otel4s.semconv.attributes.{ErrorAttributes, ServerAttributes}
import org.typelevel.otel4s.trace.{SpanFinalizer, StatusCode, Tracer, TracerProvider}
import org.typelevel.otel4s.{Attribute, Attributes}

trait RabbitTracer[F[_]] {
  def client(client: RabbitClient[F]): TracedRabbitClient[F]
}

object RabbitTracer {

  sealed trait Config {
    private[otel4s] def tracerName: String
    private[otel4s] def constAttributes: Attributes
    private[otel4s] def clientId: Option[String]
    private[otel4s] def publishSpanSetup: PublishSpanContext => Config.SpanSetup
    private[otel4s] def processSpanSetup: ProcessSpanContext => Config.SpanSetup

    def withConstAttributes(attributes: Attributes): Config
    def addConstAttributes(head: Attribute[?], tail: Attribute[?]*): Config
    def withClientId(clientId: String): Config
    def withPublishSpanSetup(f: PublishSpanContext => Config.SpanSetup): Config
    def withProcessSpanSetup(f: ProcessSpanContext => Config.SpanSetup): Config
    def withServerAddress(serverAddress: String, serverPort: Option[Int]): Config
  }

  object Config {

    object Defaults {
      val tracerName: String = "fs2.rabbit"

      val publishSpanSetup: PublishSpanContext => SpanSetup =
        context => SpanSetup(s"publish ${context.destinationName}")

      val processSpanSetup: ProcessSpanContext => SpanSetup =
        context => SpanSetup(s"process ${context.destinationName}")

      val spanFinalizationStrategy: SpanFinalizer.Strategy = {
        case Resource.ExitCase.Errored(error) =>
          val errorType = Option(error.getClass.getCanonicalName).getOrElse(error.getClass.getName)
          val setStatus = Option(error.getMessage)
            .map(message => SpanFinalizer.setStatus(StatusCode.Error, message))
            .getOrElse(SpanFinalizer.setStatus(StatusCode.Error))

          SpanFinalizer.recordException(error) |+|
            SpanFinalizer.addAttribute(ErrorAttributes.ErrorType(errorType)) |+|
            setStatus

        case Resource.ExitCase.Canceled =>
          SpanFinalizer.addAttribute(ErrorAttributes.ErrorType("canceled")) |+|
            SpanFinalizer.setStatus(StatusCode.Error, "canceled")
      }
    }

    sealed trait SpanSetup {
      def spanName: String
      def attributes: Attributes
      def finalizationStrategy: SpanFinalizer.Strategy
    }

    object SpanSetup {
      def apply(
          spanName: String,
          attributes: Attributes,
          finalizationStrategy: SpanFinalizer.Strategy
      ): SpanSetup =
        SpanSetupImpl(spanName, attributes, finalizationStrategy)

      private[RabbitTracer] def apply(spanName: String): SpanSetup =
        SpanSetup(spanName, Attributes.empty, Defaults.spanFinalizationStrategy)

      final private case class SpanSetupImpl(
          spanName: String,
          attributes: Attributes,
          finalizationStrategy: SpanFinalizer.Strategy
      ) extends SpanSetup
    }

    val default: Config =
      ConfigImpl(
        tracerName = Defaults.tracerName,
        constAttributes = Attributes.empty,
        clientId = None,
        publishSpanSetup = Defaults.publishSpanSetup,
        processSpanSetup = Defaults.processSpanSetup
      )

    final private case class ConfigImpl(
        tracerName: String,
        constAttributes: Attributes,
        clientId: Option[String],
        publishSpanSetup: PublishSpanContext => SpanSetup,
        processSpanSetup: ProcessSpanContext => SpanSetup
    ) extends Config {
      override def withConstAttributes(attributes: Attributes): Config = copy(constAttributes = attributes)

      override def addConstAttributes(head: Attribute[?], tail: Attribute[?]*): Config =
        copy(constAttributes = constAttributes + head ++ tail)

      override def withClientId(clientId: String): Config = copy(clientId = Some(clientId))

      override def withPublishSpanSetup(f: PublishSpanContext => SpanSetup): Config = copy(publishSpanSetup = f)

      override def withProcessSpanSetup(f: ProcessSpanContext => SpanSetup): Config = copy(processSpanSetup = f)

      override def withServerAddress(serverAddress: String, serverPort: Option[Int]): Config =
        copy(
          constAttributes = constAttributes +
            ServerAttributes.ServerAddress(serverAddress) ++
            ServerAttributes.ServerPort.maybe(serverPort.map(_.toLong))
        )
    }
  }

  def apply[F[_]](implicit rabbitTracer: RabbitTracer[F]): RabbitTracer[F] = rabbitTracer

  def noop[F[_]: Concurrent]: RabbitTracer[F] = new Noop[F]

  def create[F[_]: Concurrent: TracerProvider](config: Config): F[RabbitTracer[F]] =
    TracerProvider[F]
      .tracer(config.tracerName)
      .withVersion(BuildInfo.version)
      .get
      .map(implicit tracer => new Impl[F](config))

  def resource[F[_]: Concurrent: TracerProvider](config: Config): Resource[F, RabbitTracer[F]] =
    Resource.eval(create(config))

  final private class Impl[F[_]: Concurrent: Tracer](config: Config) extends RabbitTracer[F] {
    override def client(client: RabbitClient[F]): TracedRabbitClient[F] =
      new TracedRabbitClient.Impl[F](client, config)
  }

  final private class Noop[F[_]: Concurrent] extends RabbitTracer[F] {
    override def client(client: RabbitClient[F]): TracedRabbitClient[F] =
      TracedRabbitClient.noop(client)
  }
}
