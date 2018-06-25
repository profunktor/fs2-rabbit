fs2-rabbit
==========

[![Build Status](https://travis-ci.org/gvolpe/fs2-rabbit.svg?branch=master)](https://travis-ci.org/gvolpe/fs2-rabbit)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/011c5931cd3945b3a88eb725f18bbf88)](https://www.codacy.com/app/volpegabriel/fs2-rabbit?utm_source=github.com&utm_medium=referral&utm_content=gvolpe/fs2-rabbit&utm_campaign=badger)
[![Gitter Chat](https://badges.gitter.im/fs2-rabbit/fs2-rabbit.svg)](https://gitter.im/fs2-rabbit/fs2-rabbit)
[![codecov](https://codecov.io/gh/gvolpe/fs2-rabbit/branch/master/graph/badge.svg)](https://codecov.io/gh/gvolpe/fs2-rabbit)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.gvolpe/fs2-rabbit_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cfs2-rabbit)


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
