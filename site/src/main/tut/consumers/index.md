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

When creating a consumer, either by using `createAutoAckConsumer` or `createAckerConsumer`, you'll need an instance of `EnvelopeDecoder[A]` available in scope. A default instance for `UTF-8` encoding is provided by the library but if you wish to decode the envelope's payload in a different format you'll need to provide an implicit instance. Here's the definition:

```scala
trait EnvelopeDecoder[F[_], A] {
  def decode(raw: Array[Byte], properties: AmqpProperties): F[A]
}
```

You could check the `contentType` and `contentEncoding` values to decide how to decode the payload.

- **[AutoAckConsumer](./autoackconsumer.html)**: A consumer that acknowledges message consumption automatically.
- **[AckerConsumer](./ackerconsumer)**: A consumer that delegates the responsibility to acknowledge message consumption to the user.
- **[Consuming Json](./json.html)**: Consuming Json messages using the `fs2-rabbit-circe` module.
