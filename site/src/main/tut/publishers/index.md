---
layout: docs
title:  "Publishers"
number: 10
---

# Publishers

Publishing are blocking actions in the underlying Java client so you need to pass in a `cats.effect.Blocker` when creating a publisher. The examples demonstrate how to create one.

- **[Publisher](./publisher.html)**: A simple message publisher.
- **[Publisher with Listener](./publisher-with-listener.html)**: A publisher with a listener for messages that can not be routed.
- **[Publishing Json](./json.html)**: Publishing Json messages using the `fs2-rabbit-circe` module.
