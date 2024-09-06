---
layout: docs
title:  "Configuration"
number: 1
---

# Configuration

The main `RabbitMQ` configuration should be defined as `Fs2RabbitConfig`. You choose how to get the information, either from an `application.conf` file, from the environment or provided by an external system. A popular option that fits well the tech stack is [Pure Config](https://pureconfig.github.io/).

```scala mdoc:silent
import cats.data.NonEmptyList
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}

import scala.concurrent.duration._

val config = Fs2RabbitConfig(
  virtualHost = "/",
  nodes = NonEmptyList.one(
    Fs2RabbitNodeConfig(
      host = "127.0.0.1",
      port = 5672
    )
  ),
  username = Some("guest"),
  password = Some("guest"),
  ssl = false,
  connectionTimeout = 3.seconds,
  requeueOnNack = false,
  requeueOnReject = false,
  internalQueueSize = Some(500),
  requestedHeartbeat = 30.seconds,
  automaticRecovery = true,
  automaticTopologyRecovery = true,
  clientProvidedConnectionName = Some("app:rabbit")
)
```

The `internalQueueSize` indicates the size of the fs2's bounded queue used internally to communicate with the AMQP Java driver.
The `automaticRecovery` indicates whether the AMQP Java driver should try to [recover broken connections](https://www.rabbitmq.com/api-guide.html#recovery).
The `requestedHeartbeat` indicates [heartbeat timeout](https://www.rabbitmq.com/heartbeats.html#using-heartbeats-in-java). Should be non-zero and lower than 60.
