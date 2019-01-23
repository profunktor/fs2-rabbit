---
layout: docs
title:  "Publishing Json"
number: 12
---

# Publishing Json

A stream-based `Json Encoder` that can be connected to a `Publisher` is provided by the extra dependency `fs2-rabbit-circe`. Implicit encoders for your classes must be on scope. You can use `Circe`'s codec auto derivation for example:

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder
import com.github.gvolpe.fs2rabbit.model._
import fs2.Stream
import io.circe.generic.auto._

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

object ioEncoder extends Fs2JsonEncoder

def program(publisher: AmqpMessage[String] => IO[Unit]) = {
  import ioEncoder._

  val message = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)
  Stream(message).covary[IO] map jsonEncode[Person] evalMap publisher
}
```

If you need to modify the output format, you can pass your own `io.circe.Printer` to the constructor of `Fs2JsonEncoder` (defaults to `Printer.noSpaces`).
