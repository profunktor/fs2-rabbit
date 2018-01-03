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

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, Matchers}

class SafeConfigReaderSpec extends FlatSpecLike with Matchers {

  it should "read the configuration without throwing exceptions" in {
    val confReader = new SafeConfigReader(ConfigFactory.load("reference.conf"))

    confReader.string("fs2-rabbit.connection.host") should be(Some("127.0.0.1"))
    confReader.int("fs2-rabbit.connection.port") should be(Some(5672))
    confReader.boolean("fs2-rabbit.requeue-on-nack") should be(Some(false))
    confReader.string("fs2-rabbit.nope") should be(None)
  }

}
