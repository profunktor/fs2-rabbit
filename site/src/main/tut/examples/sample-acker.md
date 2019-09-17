---
layout: docs
title:  "Single AckerConsumer"
number: 16
---

# Single AckerConsumer

Here we create a single `AckerConsumer`, a single `Publisher` and finally we publish two messages: a simple `String` message and a `Json` message by using the `fs2-rabbit-circe` extension.

```tut:book:silent
import java.nio.charset.StandardCharsets.UTF_8

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.json.Fs2JsonEncoder
import dev.profunktor.fs2rabbit.model.AckResult.Ack
import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{LongVal, StringVal}
import dev.profunktor.fs2rabbit.model._
import fs2.{Pipe, Pure, Stream}
import java.util.concurrent.Executors

class Flow[F[_]: Concurrent, A](
    consumer: Stream[F, AmqpEnvelope[A]],
    acker: AckResult => F[Unit],
    logger: Pipe[F, AmqpEnvelope[A], AckResult],
    publisher: AmqpMessage[String] => F[Unit]
) {

  import io.circe.generic.auto._

  case class Address(number: Int, streetName: String)
  case class Person(id: Long, name: String, address: Address)

  private val jsonEncoder = new Fs2JsonEncoder
  import jsonEncoder.jsonEncode

  val jsonPipe: Pipe[Pure, AmqpMessage[Person], AmqpMessage[String]] = _.map(jsonEncode[Person])

  val simpleMessage =
    AmqpMessage("Hey!", AmqpProperties(headers = Map("demoId" -> LongVal(123), "app" -> StringVal("fs2RabbitDemo"))))
  val classMessage = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

  val flow: Stream[F, Unit] =
    Stream(
      Stream(simpleMessage).covary[F].evalMap(publisher),
      Stream(classMessage).covary[F].through(jsonPipe).evalMap(publisher),
      consumer.through(logger).evalMap(acker)
    ).parJoin(3)

}

class AckerConsumerDemo[F[_]: Concurrent: Timer](R: Fs2Rabbit[F]) {

  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")
  implicit val stringMessageEncoder =
    Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s => s.copy(payload = s.payload.getBytes(UTF_8)).pure[F])

  def logPipe: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =>
    Sync[F].delay(s"Consumed: $amqpMsg").as(Ack(amqpMsg.deliveryTag))
  }

  val publishingFlag: PublishingFlag = PublishingFlag(mandatory = true)

  // Run when there's no consumer for the routing key specified by the publisher and the flag mandatory is true
  val publishingListener: PublishReturn => F[Unit] = pr => Sync[F].delay(s"Publish listener: $pr")

  val resources: Resource[F, (AMQPChannel, Blocker)] =
    for {
      channel    <- R.createConnectionChannel
      blockingES = Resource.make(Sync[F].delay(Executors.newCachedThreadPool()))(es => Sync[F].delay(es.shutdown()))
      blocker    <- blockingES.map(Blocker.liftExecutorService)
    } yield (channel, blocker)

  val program: F[Unit] = resources.use {
    case (channel, blocker) =>
      implicit val rabbitChannel = channel
      for {
        _ <- R.declareQueue(DeclarationQueueConfig.default(queueName))
        _ <- R.declareExchange(exchangeName, ExchangeType.Topic)
        _ <- R.bindQueue(queueName, exchangeName, routingKey)
        publisher <- R.createPublisherWithListener[AmqpMessage[String]](exchangeName,
                                                                        routingKey,
                                                                        publishingFlag,
                                                                        publishingListener,
                                                                        blocker)
        (acker, consumer) <- R.createAckerConsumer[String](queueName)
        result            = new Flow[F, String](consumer, acker, logPipe, publisher).flow
        _                 <- result.compile.drain
      } yield ()
  }
}

```

At the edge of out program we define our effect, `cats.effect.IO` in this case, and ask to evaluate the effects:

```tut:book:silent
import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import dev.profunktor.fs2rabbit.resiliency.ResilientStream

object IOAckerConsumer extends IOApp {

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    nodes = NonEmptyList.one(
      Fs2RabbitNodeConfig(
        host = "127.0.0.1",
        port = 5672
      )
    ),
    username = Some("guest"),
    password = Some("guest"),
    ssl = false,
    connectionTimeout = 3,
    requeueOnNack = false,
    internalQueueSize = Some(500),
    automaticRecovery = true
  )

  override def run(args: List[String]): IO[ExitCode] =
    Fs2Rabbit[IO](config).flatMap { client =>
      ResilientStream
        .runF(new AckerConsumerDemo[IO](client).program)
        .as(ExitCode.Success)
    }

}
```
