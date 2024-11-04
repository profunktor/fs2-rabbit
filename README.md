fs2-rabbit
==========

[![CI Status](https://github.com/profunktor/fs2-rabbit/workflows/Build/badge.svg)](https://github.com/profunktor/fs2-rabbit/actions)
[![Gitter Chat](https://badges.gitter.im/profunktor-dev/fs2-rabbit.svg)](https://gitter.im/profunktor-dev/fs2-rabbit)
[![Maven Central](https://img.shields.io/maven-central/v/dev.profunktor/fs2-rabbit_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cfs2-rabbit) <a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>
[![MergifyStatus](https://img.shields.io/endpoint.svg?url=https://gh.mergify.io/badges/profunktor/fs2-rabbit&style=flat)](https://mergify.io)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-brightgreen.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Stream-based library for [RabbitMQ](https://www.rabbitmq.com/) built-in on top of [Fs2](http://fs2.io/) and
the [RabbitMq Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

## Dependencies

> [!NOTE]
> - From `3.6.0` onwards, the library is published only for Scala `3.x` and `2.13.x`
> - For Scala `2.12.x` use the latest `<= 3.5.x` versions.
> - Previous artifacts `<= 2.0.0-RC1` were published using the `com.github.gvolpe` group id (see [migration
    guide](https://github.com/profunktor/fs2-rabbit/wiki/Migration-guide-(Vim)))

Add this to your build.sbt:

```scala
libraryDependencies += "dev.profunktor" %% "fs2-rabbit" % Version
```

And this one if you would like to have Json support:

```scala
libraryDependencies += "dev.profunktor" %% "fs2-rabbit-circe" % Version
```

## Usage Guide

Check the [official guide](https://fs2-rabbit.profunktor.dev/guide.html) for updated compiling examples.

## Adopters

| Company                                                       | Description                                                     |
|---------------------------------------------------------------|-----------------------------------------------------------------|
| [Cognotekt](http://www.cognotekt.com/en)                      | Microservice workflow management in Insuretech AI applications. |
| [ITV](https://www.itv.com/)                                   | Internal microservices interaction.                             |
| [Klarna](https://www.klarna.com/us/)                          | Microservice for Fintech services.                              |
| [Philips Lighting](http://www.lighting.philips.com/main/home) | Internal microservices interaction.                             |
| [Descartes Kontainers](https://kontainers.com)                | Microservice workflow management - Logistics applications.      |
| [Codacy](https://www.codacy.com)                              | Internal microservices interaction.                             |
| [Budgetbakers](https://budgetbakers.com)                      | Internal microservices communication - Fintech                  |

## Running tests locally

Start a `RabbitMQ` instance using `docker compose` (recommended):

```bash
> docker compose up
> sbt +test
```

## Code of Conduct

See the [Code of Conduct](https://fs2-rabbit.profunktor.dev/CODE_OF_CONDUCT)

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
