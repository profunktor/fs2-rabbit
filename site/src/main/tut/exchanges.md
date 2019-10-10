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
import cats.implicits._
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model._

val x1 = ExchangeName("x1")
val x2 = ExchangeName("x2")

def exchanges(R: RabbitClient[IO]) =
  R.createConnectionChannel.use { implicit channel =>
    R.declareExchange(x1, ExchangeType.Topic) *>
    R.declareExchange(x2, ExchangeType.FanOut)
  }
```

An `Exchange` can be declared passively, meaning that the `Exchange` is required to exist, whatever its properties.

```tut:book:silent
import cats.effect.IO
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model._

val x = ExchangeName("x")

def exchanges(R: RabbitClient[IO]) =
  R.createConnectionChannel.use { implicit channel =>
    R.declareExchangePassive(x)
  }
```

### Binding Exchanges

Two exchanges can be bound together by providing a `RoutingKey` and some extra arguments with `ExchangeBindingArgs`.

```tut:book:silent
def binding(R: RabbitClient[IO])(implicit channel: AMQPChannel) =
  R.bindExchange(x1, x2, RoutingKey("rk"), ExchangeBindingArgs(Map.empty))
```

Read more about `Exchanges` and `ExchangeType` [here](https://www.rabbitmq.com/tutorials/amqp-concepts.html#exchanges).
