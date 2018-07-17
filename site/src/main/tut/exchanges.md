---
layout: docs
title:  "Exchanges"
number: 4
---

# Exchanges

Before getting into the `Consumers` section there are two things you need to know about `Exchanges`.

### Declaring a Exchange

Declaring a `Exchange` means that if already exists it's going to get a reference to it or otherwise it will create it.

```tut:book
import cats.effect.IO
import com.itv.fs2rabbit.interpreter.Fs2Rabbit
import com.itv.fs2rabbit.model._
import fs2._

val x1 = ExchangeName("x1")
val x2 = ExchangeName("x2")

def exchanges(implicit F: Fs2Rabbit[IO]) = F.createConnectionChannel flatMap { implicit channel =>
  for {
    _ <- F.declareExchange(x1, ExchangeType.Topic)
    _ <- F.declareExchange(x2, ExchangeType.FanOut)
  } yield ()
}
```

### Binding Exchanges

Two exchanges can be bound together by providing a `RoutingKey` and some extra arguments with `ExchangeBindingArgs`.

```tut:book
def binding(F: Fs2Rabbit[IO])(implicit channel: AMQPChannel) =
  F.bindExchange(x1, x2, RoutingKey("rk"), ExchangeBindingArgs(Map.empty))
```

Read more about `Exchanges` and `ExchangeType` [here](https://www.rabbitmq.com/tutorials/amqp-concepts.html#exchanges).
