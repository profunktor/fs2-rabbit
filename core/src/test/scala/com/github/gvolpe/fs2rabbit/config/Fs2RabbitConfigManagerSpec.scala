/*
 * Copyright 2017 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.config

import cats.effect.IO
import org.scalatest.{FlatSpecLike, Matchers}

class Fs2RabbitConfigManagerSpec extends FlatSpecLike with Matchers {

  it should "read the configuration giving default values if not found" in {
    val config = new Fs2RabbitConfigManager[IO].config.unsafeRunSync()
    config.connectionTimeout should be(3)
    config.host should be("127.0.0.1")
    config.port should be(5672)
    config.useSsl should be(false)
    config.requeueOnNack should be(false)
    config.username should be(Some("guest"))
    config.password should be(Some("guest"))
  }

}
