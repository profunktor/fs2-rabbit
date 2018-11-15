---
layout: docs
title:  "Consumers"
number: 6
---

# Consumers

There are two types of consumer: `AutoAck` and `AckerConsumer`. Each of them are parameterized on the effect type (eg. `IO`) and the data type it consumes (the payload). It's defined as below:

```scala
type StreamConsumer[F[_], A] = Stream[F, AmqpEnvelope[A]]
```

# `EnvelopeDecoder`

When creating a consumer, either by using `createAutoAckConsumer` or `createAckerConsumer`, you'll need an instance of `EnvelopeDecoder[F, A]` available in scope. A default instance for Strings is provided by the library; it will use the `contentEncoding` of the message to determine the charset and fall back to UTF-8 if that is not present. If you wish to decode the envelope's payload in a different format you'll need to provide an implicit instance. Here's the definition:

```scala
type EnvelopeDecoder[F[_], A] = Kleisli[F, AmqpEnvelope[Array[Byte]], A]
```

`Kleisli[F, AmqpEnvelope[Array[Byte]], A]` is a wrapper around a function `AmqpEnvelope[Array[Byte]] => F[A]`. You can for example write an `EnvelopeDecoder` for the payload bytes thusly:
```scala
implicit def bytesDecoder[F[_]]: EnvelopeDecoder[F, Array[Byte]] =
  Kleisli(_.payload.pure[F])
```

You can write all your `EnvelopeDecoder` instances this way, but it's usually easier to make use of existing instances. `Kleisli` forms a Monad, so you can use all the usual combinators like `map`:
```scala
case class Foo(s: String)
object Foo {
  implicit def fooDecoder[F[_]: Functor]: EnvelopeDecoder[F, Foo] =
    EnvelopeDecoder[F, String].map(Foo.apply)
}
```

Another useful combinator is `flatMapF`. For example a decoder for circe's JSON type can be defined as follows:
```scala
import io.circe.parser._
implicit def jsonDecoder[F[_]](implicit F: MonadError[F, Throwable]): EnvelopeDecoder[F] =
  EnvelopeDecoder[F, String].flatMapF(s => F.fromEither(parse(s)))

```

For more details, please refer to the the [`Kleisli` documentation](https://typelevel.org/cats/datatypes/kleisli.html).

The library comes with a number of `EnvelopeDecoder`s predefined in `object EnvelopeDecoder`. These allow you to easily access both optional and non-optional header fields, the `AmqpProperties` object and the payload (as an `Array[Byte]`). Refer to the source code for details.



- **[AutoAckConsumer](./autoackconsumer.html)**: A consumer that acknowledges message consumption automatically.
- **[AckerConsumer](./ackerconsumer)**: A consumer that delegates the responsibility to acknowledge message consumption to the user.
- **[Consuming Json](./json.html)**: Consuming Json messages using the `fs2-rabbit-circe` module.
