---
layout: docs
title:  "Consuming Json"
number: 9
---

# Json message Consuming

A stream-based `Json Decoder` that can be connected to a stream of `AmqpEnvelope` is provided by the extra dependency `fs2-rabbit-circe`. Implicit decoders for your classes must be on scope. You can use `Circe`'s codec auto derivation for example:

```scala mdoc:silent
import cats.effect.IO
import dev.profunktor.fs2rabbit.json.Fs2JsonDecoder
import dev.profunktor.fs2rabbit.model.AckResult.*
import dev.profunktor.fs2rabbit.model.*
import io.circe.*
import io.circe.generic.auto.*
import fs2.*

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

object ioDecoder extends Fs2JsonDecoder

def program(consumer: Stream[IO, AmqpEnvelope[String]], acker: AckResult => IO[Unit], errorSink: Pipe[IO, Error, Unit], processorSink: Pipe[IO, (Person, DeliveryTag), Unit]) = {
  import ioDecoder._

  consumer.map(jsonDecode[Person]).flatMap {
    case (Left(error), tag) => (Stream.eval(IO(error)).through(errorSink)).as(NAck(tag)).evalMap(acker)
    case (Right(msg), tag)  => Stream.eval(IO((msg, tag))).through(processorSink)
  }
}
```
