/*
 * Copyright 2017-2019 Gabriel Volpe
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

import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra.Channeller
import com.github.gvolpe.fs2rabbit.effects.Log
import com.github.gvolpe.fs2rabbit.model
import com.github.gvolpe.fs2rabbit.model.{AMQPChannel, AMQPConnection, RabbitChannel}
import fs2.Stream

class ChannelStream[F[_]](
    connection: AMQPConnection
)(implicit F: Sync[F], L: Log[F])
    extends Channeller[Stream[F, ?]] {

  private val acquireChannel: F[AMQPChannel] =
    for {
      chan <- F.delay(connection.value.createChannel())
    } yield RabbitChannel(chan)

  override def createChannel: Stream[F, model.AMQPChannel] =
    Stream
      .bracket(acquireChannel) {
        case RabbitChannel(chan) =>
          L.info(s"Releasing channel: $chan previously acquired.") *>
            F.delay { if (chan.isOpen) chan.close() }
      }
}
