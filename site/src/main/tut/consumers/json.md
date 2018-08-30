---
layout: docs
title:  "Consuming Json"
number: 9
---

# Json message Consuming

A stream-based `Json Decoder` that can be connected to a `StreamConsumer` is provided by the extra dependency `fs2-rabbit-circe`. Implicit decoders for your classes must be on scope. You can use `Circe`'s codec auto derivation for example:

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.json.Fs2JsonDecoder
import com.github.gvolpe.fs2rabbit.model.AckResult._
import com.github.gvolpe.fs2rabbit.model._
import io.circe._
import io.circe.generic.auto._
import fs2._

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

object ioDecoder extends Fs2JsonDecoder[IO]

def program(consumer: StreamConsumer[IO], acker: StreamAcker[IO], errorSink: Sink[IO, Error], processorSink: Sink[IO, (Person, DeliveryTag)]) = {
  import ioDecoder._

  (consumer through jsonDecode[Person]).flatMap {
    case (Left(error), tag) => (Stream.eval(IO(error)) to errorSink).map(_ => NAck(tag)) to acker
    case (Right(msg), tag)  => Stream.eval(IO((msg, tag))) to processorSink
  }
}
```
