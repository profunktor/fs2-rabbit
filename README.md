![logo](fs2-rabbit.png) fs2-rabbit
==========

[![Build Status](https://travis-ci.org/gvolpe/fs2-rabbit.svg?branch=master)](https://travis-ci.org/gvolpe/fs2-rabbit)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/011c5931cd3945b3a88eb725f18bbf88)](https://www.codacy.com/app/volpegabriel/fs2-rabbit?utm_source=github.com&utm_medium=referral&utm_content=gvolpe/fs2-rabbit&utm_campaign=badger)
[![Gitter Chat](https://badges.gitter.im/fs2-rabbit/fs2-rabbit.svg)](https://gitter.im/fs2-rabbit/fs2-rabbit)
[![codecov](https://codecov.io/gh/gvolpe/fs2-rabbit/branch/master/graph/badge.svg)](https://codecov.io/gh/gvolpe/fs2-rabbit)

Stream-based library for [RabbitMQ](https://www.rabbitmq.com/) built-in on top of [Fs2](https://github.com/functional-streams-for-scala/fs2) and the [RabbitMq Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

***Disclaimer:** It's still under development and it was created just to solve my specific cases, but more features will be added as needed. Contributors are welcomed :)*

## Dependencies

Add the only dependency to your build.sbt:

```scala
libraryDependencies += "com.github.gvolpe" %% "fs2-rabbit" % "0.1.2"
```

`fs2-rabbit` has the following dependencies and it's cross compiled to Scala `2.11.12` and `2.12.4`:

| Dependency  | Version    |
| ----------- |:----------:|
| cats        | 1.0.1      |
| cats-effect | 0.8        |
| fs2         | 0.10.2     |
| circe       | 0.9.1      |
| amqp-client | 4.1.0      |

## Usage

#### Configuration

By default, fs2-rabbit will look into the **application.conf** file for the configuration of the library, here's an example:

```scala
fs2-rabbit {
  connection {
    virtual-host = "/"
    host = "127.0.0.1"
    username = "guest"
    password = "guest"
    port = 5672
    ssl = false
    connection-timeout = 3
  }

  requeue-on-nack = false
}
```

See reference.conf for more.

#### Creating connection, channel, "acker-consumer" and publisher + declare queue and exchange + binding queue

Connection and Channel will be acquired in a safe way, so in case of an error, the resources will be cleaned up.

`F` represents the effect type. In the examples both `cats.effect.IO` and `monix.eval.Task` are used but it's possible to use any other effect with an implicit instance of `cats.effect.Effect[F]` available.

```scala
implicit val ec: ExecutionContext = ???
implicit val F: Fs2Rabbit[IO] = Fs2Rabbit[IO]

val exchangeName  = ExchangeName("ex")
val queueName     = QueueName("daQ")
val routingKey    = RoutingKey("rk")

val program = F.createConnectionChannel flatMap { implicit channel => 	      // Stream[F, AMQPChannel]
  for {
    _                 <- F.declareQueue(queueName)                            // Stream[F, Unit]
    _                 <- F.declareExchange(exchangeName, ExchangeType.Topic)  // Stream[F, Unit]
    _                 <- F.bindQueue(queueName, exchangeName, routingKey)     // Stream[F, Unit]
    ackerConsumer     <- F.createAckerConsumer(queueName)	              // (StreamAcker[F], StreamConsumer[F])
    (acker, consumer) = ackerConsumer
    publisher         <- F.createPublisher(exchangeName, routingKey)	      // StreamPublisher[F]
    _                 <- doSomething(consumer, acker, publisher)
  } yield ()
}

// this will give you an Effect describing your program F[Unit]
val effect: F[Unit] = program.run

// if using cats.effect.IO you can execute it like this
effect.unsafeRunSync()

// StreamAcker is a type alias for Sink[F, AckResult]
// StreamConsumer is a type alias for Stream[F, AmqpEnvelope]
// StreamPublisher is a type alias for Sink[F, AmqpMessage[String]]

```

#### Message Consuming and Acknowledge

It is possible to create either an **autoAckConsumer** or an **ackerConsumer**. If we choose the first one then we only need to worry about consuming the message. If we choose the latter instead, then we are in control of acking / nacking back to RabbitMQ. Here's a simple example on how you can do it:

```scala
import com.github.gvolpe.fs2rabbit.model._
import fs2.{Pipe, Stream}

def logPipe: Pipe[F, AmqpEnvelope, AckResult] = { streamMsg =>
  for {
    amqpMsg <- streamMsg
    _       <- Stream.eval(F.delay(println(s"Consumed: $amqpMsg")))
  } yield Ack(amqpMsg.deliveryTag)
}

consumer through logPipe to acker
```

#### Consumer options

When creating a consumer, it's also possible to indicate whether it is exclusive, non-local and the basic QOS among others.

Both `createAckerConsumer` and `createAutoackConsumer` methods support two extra arguments: A `BasicQos` and a `ConsumerArgs`. By default, the basic QOS is set to a prefetch size of 0, a prefetch count of 1 and global is set to false. The ConsumerArgs is by default None since it's optional. When defined, you can indicate `consumerTag` (default is ""), `noLocal` (default is false), `exclusive` (default is false) and `args` (default is an empty Map[String, AnyRef]).

#### Json message Consuming

A stream-based Json Decoder that can be connected to a StreamConsumer is provided out of the box. Implicit decoders for your classes must be on scope (you can use Circe's codec auto derivation):

```scala
import io.circe._
import io.circe.generic.auto._

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

private val jsonDecoder = new Fs2JsonDecoder[F]
import jsonDecoder._

(consumer through jsonDecode[Person]) flatMap {
  case (Left(error), tag) => (Stream.eval(F.delay(error)) to errorSink).map(_ => Nack(tag)) to acker
  case (Right(msg), tag)  => Stream.eval(F.delay((msg, tag))) to processorSink
}
```

#### Publishing

To publish a simple String message is very simple:

```scala
import com.github.gvolpe.fs2rabbit.model._
import fs2._

val message = AmqpMessage("Hello world!", AmqpProperties.empty)

Stream(message).covary[F] to publisher
```

#### Publishing Json Messages

A stream-based Json Encoder that can be connected to a StreamPublisher is provided out of the box. Very similar to the Json Decoder shown above, but in this case, implicit encoders for your classes must be on scope (again you can use Circe's codec auto derivation):

```scala
import com.github.gvolpe.fs2rabbit.model._
import io.circe.generic.auto._
import fs2._

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

private val jsonEncoder = new Fs2JsonEncoder[F]
import jsonEncoder._

val message = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

Stream(message).covary[F] through jsonEncode[F, Person] to publisher
```

#### Resiliency

If you want your program to run forever with automatic error recovery you can choose to run your program in a loop that will restart every certain amount of specified time. An useful `StreamLoop` object that you can use to achieve this is provided by the library.

So, for the program defined above, this would be an example of a resilient app that restarts after 1 second and then exponentially (1, 2, 4, 8, etc) in case of failure:

```scala
import com.github.gvolpe.fs2rabbit.StreamLoop
import scala.concurrent.duration._

StreamLoop.run(() => program, 1.second)
```

See the [examples](https://github.com/gvolpe/fs2-rabbit/tree/master/examples/src/main/scala/com/github/gvolpe/fs2rabbit/examples) to learn more!

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
