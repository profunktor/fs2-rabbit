---
layout: docs
title:  "Exchanges"
number: 4
---

# Exchanges

Before getting into the `Consumers` section there are two things you need to know about `Exchanges`.

### Declaring a Exchange

Declaring an `Exchange` will either create a new one or, in case an exchange of that name was already declared, returns a reference to an existing one.
If the `Exchange` already exists, but has different properties (type, internal, ...) the action will fail.

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._

val x1 = ExchangeName("x1")
val x2 = ExchangeName("x2")

def exchanges(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel use { implicit channel =>
  for {
    _ <- F.declareExchange(x1, ExchangeType.Topic)
    _ <- F.declareExchange(x2, ExchangeType.FanOut)
  } yield ()
}
```

An `Exchange` can be declared passively, meaning that the `Exchange` is required to exist, whatever its properties.

```tut:book:silent
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.model._

val x = ExchangeName("x")

def exchanges(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel use { implicit channel =>
  for {
    _ <- F.declareExchangePassive(x)
  } yield ()
}
```

### Binding Exchanges

Two exchanges can be bound together by providing a `RoutingKey` and some extra arguments with `ExchangeBindingArgs`.

```tut:book:silent
def binding(F: Fs2Rabbit[IO])(implicit channel: AMQPChannel) =
  F.bindExchange(x1, x2, RoutingKey("rk"), ExchangeBindingArgs(Map.empty))
```

Read more about `Exchanges` and `ExchangeType` [here](https://www.rabbitmq.com/tutorials/amqp-concepts.html#exchanges).
