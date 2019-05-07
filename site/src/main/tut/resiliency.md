---
layout: docs
title:  "Resiliency"
number: 13
---

# Resiliency

If you want your program to run forever with automatic error recovery you can choose to run your program in a loop that will restart every certain amount of specified time with an exponential backoff then `ResilientStream` is all you're looking for.

For a given `Fs2 Rabbit` program defined as `Stream[F, Unit]`, a resilient app will look as follow:

```tut:book:silent
import cats.effect.IO
import dev.profunktor.fs2rabbit.resiliency.ResilientStream
import fs2._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

implicit val timer = IO.timer(ExecutionContext.global)

val program: Stream[IO, Unit] = Stream.eval(IO.unit)

ResilientStream.run(program, 1.second)
```

This program will run forever and in the case of failure it will be restarted after 1 second and then exponentially after 2 seconds, 4 seconds, 8 seconds, etc.

For a program defined as `F[Unit]` see the equivalent `ResilientStream.runF`.

See the [examples](https://github.com/gvolpe/fs2-rabbit/tree/master/examples/src/main/scala/dev.profunktor/fs2rabbit/examples) to learn more!
