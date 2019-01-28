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

import cats.effect.IO
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.model.AMQPChannel
import com.github.gvolpe.fs2rabbit.{DockerRabbit, IOAssertion}
import org.scalatest.{FlatSpec, Matchers}

class ConnectionStreamSpec extends FlatSpec with Matchers with DockerRabbit {

  "ConnectionStream" should "create and close connection using bracket" in IOAssertion {
    def openAssertion(channel: AMQPChannel) =
      IO.delay {
        assert(channel.value.isOpen, "Channel should be open")
        assert(channel.value.getConnection.isOpen, "Connection should be open")
      }.void

    for {
      rabbit     <- Fs2Rabbit[IO](rabbitConfig)
      connection <- rabbit.createConnectionChannel.evalTap(openAssertion).compile.lastOrError
    } yield {
      assert(!connection.value.isOpen, "Channel should be closed")
      assert(!connection.value.getConnection.isOpen, "Connection should be closed")
    }
  }

}
