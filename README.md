fs2-rabbit
==========

Stream-based library for [RabbitMQ](https://www.rabbitmq.com/) built-in on top of [Fs2](https://github.com/functional-streams-for-scala/fs2) and the [RabbitMq Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

## Dependencies

Add the only dependency to your build.sbt:

```scala
libraryDependencies += "com.github.gvolpe" %% "fs2-rabbit" % "0.0.1-SNAPSHOT"
```

fs2-rabbit depends on fs2 v0.9.6, circe v0.5.1 and amqp-client v4.1.0.

## Usage

```scala
import com.github.gvolpe.fs2rabbit.Fs2Rabbit._

val program = for {
  connAndChannel    <- createConnectionChannel() 			// Stream[Task, (Connection, Channel)]
  (_, channel)      = connAndChannel
  _                 <- declareQueue(channel, queueName) 		// Stream[Task, Queue.DeclareOk]
  (acker, consumer) = createAckerConsumer(channel, queueName)(libS)	// (StreamAcker, StreamConsumer)
  publisher         = createPublisher(channel, "", routingKey)(libS)	// StreamPublisher
  _                 <- doSomething(consumer, acker, publisher)
} yield ()

// Only once in your program...
program.run.unsafeRun()

// StreamAcker is a type alias for Stream[Task, AckResult]
// StreamConsumer is a type alias for Stream[Task, Envelope]
// StreamPublisher is a type alias for Stream[Task, String]

```

See the Demo included in the library.

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
