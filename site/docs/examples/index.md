---
layout: docs
title:  "Examples"
number: 14
position: 2
---

# Examples

The source code for some of the examples can be found [here](https://github.com/profunktor/fs2-rabbit/tree/master/examples/src/main/scala/dev.profunktor/fs2rabbit/examples).

### Simple

- **[Single AutoAckConsumer](./sample-autoack.html)**: Example of a single `AutoAckConsumer`, a `Publisher` and `Json` data manipulation.
- **[Single AckerConsumer](./sample-acker.html)**: Example of a single `AckerConsumer`, a `Publisher` and `Json` data  manipulation.
- **[Multiple Consumers](./sample-mult-consumers.html)**: Two `Consumers` bound to queues with different `RoutingKey`, one `Publisher`.
- **[Client Metrics](./client-metrics.html)**: Use RabbitMQ Java Client metrics collector to Dropwizard.

### Advanced

- **[Multiple Connections](./sample-mult-connections.html)**: Three different `RabbitMQ` `Connections`, two `Consumers` and one `Publisher` interacting with each other.

