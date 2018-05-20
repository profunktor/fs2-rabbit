---
layout: docs
title:  "Examples"
number: 14
position: 2
---

# Examples

The source code for some of the examples can be found [here](https://github.com/gvolpe/fs2-rabbit/tree/master/examples/src/main/scala/com/github/gvolpe/fs2rabbit/examples).

### Simple
- **[Single AutoAckConsumer](./sample-autoack.html)**: Example of a single `AutoAckConsumer`, a `Publisher` and `Json` data manipulation.
- **[Single AckerConsumer](./sample-acker.html)**: Example of a single `AckerConsumer`, a `Publisher` and `Json` data  manipulation.
- **[Multiple Consumers](./sample-mult-consumers.html)**: Two `Consumers` bound to queues with different `RoutingKey`, one `Publisher`.

### Advanced

- **[Multiple Connections](./sample-mult-connections.html)**: Three different `RabbitMQ` `Connections`, two `Consumers` and one `Publisher` interacting with each other.

