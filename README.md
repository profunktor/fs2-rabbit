fs2-rabbit
==========

[![CircleCI](https://circleci.com/gh/gvolpe/fs2-rabbit.svg?style=svg)](https://circleci.com/gh/gvolpe/fs2-rabbit)
[![Gitter Chat](https://badges.gitter.im/fs2-rabbit/fs2-rabbit.svg)](https://gitter.im/fs2-rabbit/fs2-rabbit)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.gvolpe/fs2-rabbit_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cfs2-rabbit) <a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>


Stream-based library for [RabbitMQ](https://www.rabbitmq.com/) built-in on top of [Fs2](https://github.com/functional-streams-for-scala/fs2) and the [RabbitMq Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

## Dependencies

Add this to your build.sbt:

```scala
libraryDependencies += "com.github.gvolpe" %% "fs2-rabbit" % Version
```

And this one if you would like to have Json support:

```scala
libraryDependencies += "com.github.gvolpe" %% "fs2-rabbit-circe" % Version
```

## Usage Guide

Check the [official guide](https://gvolpe.github.io/fs2-rabbit/guide.html) for updated compiling examples.

## Adopters

| Company | Description |
| ------- | ----------- |
| [Cognotekt](http://www.cognotekt.com/en) | Microservice workflow management in Insuretech AI applications. |
| [ITV](https://www.itv.com/) | Internal microservices interaction. |
| [Klarna](https://www.klarna.com/us/) | Microservice for Fintech services. |
| [Philips Lighting](http://www.lighting.philips.com/main/home) | Internal microservices interaction. |
| [Free2Move](https://free2move.com) | Microservice communication. |

## Code of Conduct

See the [Code of Conduct](CODE_OF_CONDUCT.md)

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
