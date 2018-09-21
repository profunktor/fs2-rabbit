---
layout: docs
title:  "Single AutoAckConsumer"
number: 15
---

# Single AutoAckConsumer

Here we create a single `AutoAckConsumer`, a single `Publisher` and finally we publish two messages: a simple `String` message and a `Json` message by using the `fs2-rabbit-circe` extension.

```tut:book:silent
import cats.effect.{Concurrent, Sync, Timer}
import com.github.gvolpe.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder
import com.github.gvolpe.fs2rabbit.model.AckResult.Ack
import com.github.gvolpe.fs2rabbit.model.AmqpHeaderVal.{LongVal, StringVal}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.util.StreamEval
import fs2.{Pipe, Stream}

class AutoAckFlow[F[_]: Concurrent](
    consumer: StreamConsumer[F],
    logger: Pipe[F, AmqpEnvelope, AckResult],
    publisher: StreamPublisher[F])(implicit SE: StreamEval[F]) {

  import io.circe.generic.auto._

  case class Address(number: Int, streetName: String)
  case class Person(id: Long, name: String, address: Address)

  private val jsonEncoder = new Fs2JsonEncoder[F]
  import jsonEncoder.jsonEncode

  val simpleMessage =
    AmqpMessage(
        "Hey!",
        AmqpProperties(headers = Map("demoId" -> LongVal(123), "app" -> StringVal("fs2RabbitDemo"))))
  val classMessage = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

  val flow: Stream[F, Unit] =
    Stream(
      Stream(simpleMessage).covary[F] to publisher,
      Stream(classMessage).covary[F] through jsonEncode[Person] to publisher,
      consumer through logger to SE.liftSink(ack => Sync[F].delay(println(ack)))
    ).parJoin(3)

}

class AutoAckConsumerDemo[F[_]: Concurrent: Timer](implicit F: Fs2Rabbit[F], SE: StreamEval[F]) {

  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")

  def logPipe: Pipe[F, AmqpEnvelope, AckResult] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- SE.evalF[Unit](println(s"Consumed: $amqpMsg"))
    } yield Ack(amqpMsg.deliveryTag)
  }

  val program: Stream[F, Unit] = F.createConnectionChannel flatMap { implicit channel =>
    for {
      _         <- F.declareQueue(DeclarationQueueConfig.default(queueName))
      _         <- F.declareExchange(exchangeName, ExchangeType.Topic)
      _         <- F.bindQueue(queueName, exchangeName, routingKey)
      consumer  <- F.createAutoAckConsumer(queueName)
      publisher <- F.createPublisher(exchangeName, routingKey)
      result    <- new AutoAckFlow(consumer, logPipe, publisher).flow
    } yield result
  }

}
```

At the edge of out program we define our effect, `monix.eval.Task` in this case, and ask to evaluate the effects:

```tut:book:silent
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.resiliency.ResilientStream
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

object MonixAutoAckConsumer extends IOApp {

  implicit val ec = scala.concurrent.ExecutionContext.global

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(virtualHost = "/",
                                                        host = "127.0.0.1",
                                                        username = Some("guest"),
                                                        password = Some("guest"),
                                                        port = 5672,
                                                        ssl = false,
                                                        sslContext = None,
                                                        connectionTimeout = 3,
                                                        requeueOnNack = false)

  override def run(args: List[String]): IO[ExitCode] =
    Fs2Rabbit[Task](config).flatMap { implicit interpreter =>
      ResilientStream.run(new AutoAckConsumerDemo[Task].program)
    }.toIO.as(ExitCode.Success)
}
```
