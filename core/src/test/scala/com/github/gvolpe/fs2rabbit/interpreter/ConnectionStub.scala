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

import cats.effect.{IO, Resource}
import cats.effect.concurrent.Ref
import cats.implicits._
import com.github.gvolpe.fs2rabbit.algebra.Connection
import com.github.gvolpe.fs2rabbit.model.AMQPChannel
import com.rabbitmq.client.Channel

class ConnectionStub(open: Ref[IO, Boolean]) extends Connection[Resource[IO, ?]] {

  case class ChannelStub(value: Channel = null) extends AMQPChannel

  override def createConnectionChannel: Resource[IO, AMQPChannel] =
    Resource.make(open.set(true))(_ => open.set(false)).map(_ => ChannelStub())

}
