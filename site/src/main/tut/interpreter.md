---
layout: docs
title:  "Fs2 Rabbit Interpreter"
number: 2
---

# Fs2 Rabbit Interpreter

It is the main interpreter that will be interacting with `RabbitMQ`, a.k.a. the client. All it needs are a `Fs2RabbitConfig` and an implicit instance of `ConcurrentEffect[F]`.

```tut:book:silent
import cats.effect._
import com.itv.fs2rabbit.config.Fs2RabbitConfig
import com.itv.fs2rabbit.interpreter.Fs2Rabbit

object Fs2Rabbit {
  def apply[F[_]: ConcurrentEffect](config: Fs2RabbitConfig): F[Fs2Rabbit[F]] = ???
}
```

The recommended way to create the interpreter is to call `apply` and then `flatMap` to access the inner instance and make it available as an implicit. For example:

```tut:book:invisible
import com.itv.fs2rabbit.model._
val config: Fs2RabbitConfig = null
```

```tut:book
import com.itv.fs2rabbit.interpreter.Fs2Rabbit
import fs2._

import scala.concurrent.ExecutionContext.Implicits.global

def yourProgram[F[_]](implicit R: Fs2Rabbit[F]): Stream[F, Unit] = ???

Stream.eval(Fs2Rabbit[IO](config)).flatMap { implicit interpreter =>
  yourProgram[IO]
}
```

Note that since the `apply` method returns `F[Fs2Rabbit[F]]` you need to evaluate it within a streaming context using `Stream.eval` to integrate it with `yourProgram` that has the type `Stream[F, Unit]`.
