---
layout: docs
title:  "Resiliency"
number: 13
---

# Resiliency

If you want your program to run forever with automatic error recovery you can choose to run your program in a loop that will restart every certain amount of specified time with an exponential backoff. An useful `StreamLoop` object that you can use to achieve this is provided by the library.

So, for  given `Fs2 Rabbit` program defined as `Stream[F, Unit]`, a resilient app will look like follows:

```tut:book
import cats.effect.IO
import com.github.gvolpe.fs2rabbit.StreamLoop
import fs2._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

val program: Stream[IO, Unit] = Stream.eval(IO.unit)

StreamLoop.run(() => program, 1.second)
```

This program will run forever and in the case of failure it will be restarted after 1 second and then exponentially after 2 seconds, 4 seconds, 8 seconds, etc.

See the [examples](https://github.com/gvolpe/fs2-rabbit/tree/master/examples/src/main/scala/com/github/gvolpe/fs2rabbit/examples) to learn more!
