---
layout: page
title:  "Guide"
section: "guide"
position: 1
---

# Guide

#### Fs2 Rabbit Interpreter

To get started, you need to create an `Fs2Rabbit[F[_]` interpreter by providing a `Fs2RabbitConfig` and an implicit `ExecutionContext`, the only one that will be used for all the internal operations. It's recommended to do it at the very beginning of your program where all the instances are created, commonly in a `for-comprehention` or just a `flatMap` if there's not too many things to create. For example:

```tut:book
import cats.effect._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._
import fs2._

import scala.concurrent.ExecutionContext.Implicits.global

val config = Fs2RabbitConfig(
  virtualHost = "/",
  host = "127.0.0.1",
  username = Some("guest"),
  password = Some("guest"),
  port = 5672,
  ssl = false,
  connectionTimeout = 3,
  requeueOnNack = false
)

def yourProgram[F[_]](implicit R: Fs2Rabbit[F]): Stream[F, Unit] = ???

Stream.eval(Fs2Rabbit[IO](config)).flatMap { implicit interpreter =>
  yourProgram[IO]
}
```

#### Creating connection, channel, "acker-consumer" and publisher + declare queue and exchange + binding queue

Connection and Channel will be acquired in a safe way, so in case of an error, the resources will be cleaned up.

`F` represents the effect type. In the examples both `cats.effect.IO` and `monix.eval.Task` are used but it's possible to use any other effect with an implicit instance of `cats.effect.Effect[F]` available.

```tut:silent
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig

class Demo[F[_]](implicit F: Effect[F], R: Fs2Rabbit[F]) {

  val exchangeName  = ExchangeName("ex")
  val queueName     = QueueName("daQ")
  val routingKey    = RoutingKey("rk")

  def doSomething(consumer: StreamConsumer[F],
                  acker: StreamAcker[F],
                  publisher: StreamPublisher[F]): Stream[F, Unit] =
    Stream.eval(F.unit)

  val program = R.createConnectionChannel flatMap { implicit channel => 	         // Stream[F, AMQPChannel]
    for {
      _                 <- R.declareQueue(DeclarationQueueConfig.default(queueName)) // Stream[F, Unit]
      _                 <- R.declareExchange(exchangeName, ExchangeType.Topic)       // Stream[F, Unit]
      _                 <- R.bindQueue(queueName, exchangeName, routingKey)          // Stream[F, Unit]
      ackerConsumer     <- R.createAckerConsumer(queueName)	                         // (StreamAcker[F], StreamConsumer[F])
      (acker, consumer) = ackerConsumer
      publisher         <- R.createPublisher(exchangeName, routingKey)	             // StreamPublisher[F]
      _                 <- doSomething(consumer, acker, publisher)
    } yield ()
  }

  // this will give you an Effect describing your program F[Unit]
  val effect: F[Unit] = program.compile.drain

  // if using cats.effect.IO you can execute it like this
  // effect.unsafeRunSync()

  // StreamAcker is a type alias for Sink[F, AckResult]
  // StreamConsumer is a type alias for Stream[F, AmqpEnvelope]
  // StreamPublisher is a type alias for Sink[F, AmqpMessage[String]]
}
```

#### Message Consuming and Acknowledge

It is possible to create either an **autoAckConsumer** or an **ackerConsumer**. If we choose the first one then we only need to worry about consuming the message. If we choose the latter instead, then we are in control of acking / nacking back to RabbitMQ. Here's a simple example on how you can do it:

```tut:silent
class LogDemo[F[_]](consumer: StreamConsumer[F], acker: StreamAcker[F])(implicit F: Sync[F]) {

  def logPipe: Pipe[F, AmqpEnvelope, AckResult] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- Stream.eval(F.delay(println(s"Consumed: $amqpMsg")))
    } yield Ack(amqpMsg.deliveryTag)
  }

  def run: Stream[F, Unit] = consumer through logPipe to acker

}
```

#### Consumer options

When creating a consumer, it's also possible to indicate whether it is exclusive, non-local and the basic QOS among others.

Both `createAckerConsumer` and `createAutoackConsumer` methods support two extra arguments: A `BasicQos` and a `ConsumerArgs`. By default, the basic QOS is set to a prefetch size of 0, a prefetch count of 1 and global is set to false. The ConsumerArgs is by default None since it's optional. When defined, you can indicate `consumerTag` (default is ""), `noLocal` (default is false), `exclusive` (default is false) and `args` (default is an empty Map[String, AnyRef]).

#### Json message Consuming

A stream-based Json Decoder that can be connected to a StreamConsumer is provided out of the box. Implicit decoders for your classes must be on scope (you can use Circe's codec auto derivation):

```tut:silent
import com.github.gvolpe.fs2rabbit.json.Fs2JsonDecoder
import io.circe._
import io.circe.generic.auto._

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

class JsonDemo[F[_]](consumer: StreamConsumer[F], acker: StreamAcker[F], errorSink: Sink[F, Error], processorSink: Sink[F, (Person, DeliveryTag)])(implicit F: Sync[F]) {

  private val jsonDecoder = new Fs2JsonDecoder[F]
  import jsonDecoder._

  (consumer through jsonDecode[Person]) flatMap {
    case (Left(error), tag) => (Stream.eval(F.delay(error)) to errorSink).map(_ => NAck(tag)) to acker
    case (Right(msg), tag)  => Stream.eval(F.delay((msg, tag))) to processorSink
  }
}
```

#### Publishing

To publish a simple String message is very simple:

```tut:silent
class PublishingDemo[F[_]](publisher: StreamPublisher[F])(implicit F: Sync[F]) {

  def run: Stream[F, Unit] = {
    val message = AmqpMessage("Hello world!", AmqpProperties.empty)
    Stream(message).covary[F] to publisher
  }

}
```

#### Publishing Json Messages

A stream-based Json Encoder that can be connected to a StreamPublisher is provided out of the box. Very similar to the Json Decoder shown above, but in this case, implicit encoders for your classes must be on scope (again you can use Circe's codec auto derivation):

```tut:silent
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

class PublishingJsonDemo[F[_]](publisher: StreamPublisher[F])(implicit F: Sync[F]) {

  private val jsonEncoder = new Fs2JsonEncoder[F]
  import jsonEncoder._

  def run: Stream[F, Unit] = {
    val message = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)
    Stream(message).covary[F] through jsonEncode[Person] to publisher
  }
}
```

#### Resiliency

If you want your program to run forever with automatic error recovery you can choose to run your program in a loop that will restart every certain amount of specified time. An useful `StreamLoop` object that you can use to achieve this is provided by the library.

So, for the program defined above, this would be an example of a resilient app that restarts after 1 second and then exponentially (1, 2, 4, 8, etc) in case of failure:

```tut:book
import com.github.gvolpe.fs2rabbit.StreamLoop

import scala.concurrent.duration._

val program: Stream[IO, Unit] = Stream.eval(IO.unit)

StreamLoop.run(() => program, 1.second)
```

See the [examples](https://github.com/gvolpe/fs2-rabbit/tree/master/examples/src/main/scala/com/github/gvolpe/fs2rabbit/examples) to learn more!
