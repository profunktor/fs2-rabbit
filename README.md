**This is a fork**
==========

This is a temporary fork of [fs2-rabbit](https://github.com/gvolpe/fs2-rabbit).  This fork was created because,
currently, the upstream project depends on unstable libraries (cats-effect, fs2, and circe), which is undesirable
in production application.  This fork is essentially the same thing as the upstream project, but using stable
dependencies.

fs2-rabbit
==========

Stream-based library for [RabbitMQ](https://www.rabbitmq.com/) built-in on top of [Fs2](https://github.com/functional-streams-for-scala/fs2) and the [RabbitMq Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

## Dependencies

Add this to your build.sbt:

```scala
libraryDependencies += "com.itv" %% "fs2-rabbit" % Version
```

And this one if you would like to have Json support:

```scala
libraryDependencies += "com.itv" %% "fs2-rabbit-circe" % Version
```

`fs2-rabbit` has the following dependencies and it's cross compiled to Scala `2.11.12` and `2.12.6`:

| Dependency  | Version    |
| ----------- |:----------:|
| cats        | 1.1.0      |
| cats-effect | 1.0.0-RC2  |
| fs2         | 1.0.0-M1   |
| circe       | 0.10.0-M1  |
| amqp-client | 4.6.0      |

## Usage Guide

Check the [official guide](https://gvolpe.github.io/fs2-rabbit/guide.html) for updated compiling examples.

## Adopters

| Company | Description |
| ------- | ----------- |
| [Cognotekt](http://www.cognotekt.com/en) | Microservice workflow management in Insuretech AI applications. |
| [Klarna](https://www.klarna.com/us/) | Microservice for Fintech services. |
| [Philips Lighting](http://www.lighting.philips.com/main/home) | Internal microservices interaction. |

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
