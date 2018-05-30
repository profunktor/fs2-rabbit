---
layout: docs
title:  "Configuration"
number: 1
---

# Configuration

The main `RabbitMQ` configuration should be defined as `Fs2RabbitConfig`. You choose how to get the information, either from an `application.conf` file, from the environment or provided by an external system. A popular option that fits well the tech stack is [Pure Config](https://pureconfig.github.io/).

```tut:book:silent
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig

val config = Fs2RabbitConfig(
  virtualHost = "/",
  host = "127.0.0.1",
  username = Some("guest"),
  password = Some("guest"),
  port = 5672,
  ssl = false,
  sslContext = None,
  connectionTimeout = 3,
  requeueOnNack = false
)
```
