fs2-rabbit
==========

Stream-based library for [RabbitMQ](https://www.rabbitmq.com/).

### Usage

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
