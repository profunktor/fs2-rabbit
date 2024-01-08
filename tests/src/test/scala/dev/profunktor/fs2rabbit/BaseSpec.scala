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

package dev.profunktor.fs2rabbit

import cats.effect.IO
import dev.profunktor.fs2rabbit.effects.Log
import org.scalatest.EitherValues
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

trait BaseSpec extends AsyncFlatSpecLike with Matchers with EitherValues {
  def putStrLn[A](a: A): IO[Unit] = IO(println(a))

  implicit val logger: Log[IO] = new Log[IO] {
    override def info(value: => String): IO[Unit]  = putStrLn(value)
    override def error(error: => String): IO[Unit] = putStrLn(error)
  }
}
