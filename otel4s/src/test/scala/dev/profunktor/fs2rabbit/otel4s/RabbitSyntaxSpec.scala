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

import cats.effect.{IO, Ref}
import dev.profunktor.fs2rabbit.otel4s.syntax.*
import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpecLike
import org.typelevel.otel4s.trace.Tracer

class RabbitSyntaxSpec extends AnyFlatSpecLike with RabbitTracingTestSupport {

  it should "bind a RabbitClient to a RabbitTracer" in {
    val client = rabbitClient(null.asInstanceOf[dev.profunktor.fs2rabbit.program.PublishingProgram[IO]])
    val traced = client.traced(RabbitTracer.noop[IO])

    traced.underlying should be theSameInstanceAs client
  }

  it should "delegate processTraced to the implicit traced consumer" in
    withTestkit { testkit =>
      val value = envelope()

      for {
        processed    <- Ref[IO].of(0)
        moduleTracer <- testkit.tracerProvider.get("fs2.rabbit")
        result       <- {
          implicit val tracer: Tracer[IO]                         = moduleTracer
          implicit val consumer: TracedRabbitConsumer[IO, String] =
            TracedRabbitConsumer[IO, String](
              queueName = dev.profunktor.fs2rabbit.model.QueueName("orders"),
              underlying = Stream.empty,
              config = RabbitTracer.Config.default
            )

          value.processTraced(processed.updateAndGet(_ + 1))
        }
        spans        <- testkit.finishedSpans
      } yield {
        result shouldBe 1
        spans.count(_.getName == "process orders:created:orders") shouldBe 1
      }
    }
}
