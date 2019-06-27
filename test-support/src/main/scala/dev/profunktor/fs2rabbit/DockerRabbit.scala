/*
 * Copyright 2017-2019 ProfunKtor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.profunktor.fs2rabbit

import cats.effect.{ContextShift, IO}
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.ExecutionContext
import scala.sys.process._

trait DockerRabbit extends BeforeAndAfterAll { self: Suite =>

  import DockerRabbit._

  // override this if the Docker container has to be started before invocation
  // when developing tests, this likely shall be false, so there is no additional overhead starting Rabbit
  protected lazy val startContainers: Boolean = false

  protected lazy val rabbitPort: Int           = 5672
  protected lazy val rabbitUser: String        = "guest"
  protected lazy val rabbitPassword: String    = "guest"
  protected lazy val rabbitVirtualHost: String = "/"

  private var dockerInstanceId: Option[String] = None

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (startContainers) {
      assertDockerAvailable()
      dockerInstanceId = Some(startDocker(rabbitPort, rabbitUser, rabbitPassword, rabbitVirtualHost))
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    dockerInstanceId.foreach(stopDocker)
  }

  lazy val rabbitConfig = Fs2RabbitConfig(
    host = "localhost",
    port = rabbitPort,
    virtualHost = rabbitVirtualHost,
    connectionTimeout = 30,
    ssl = false,
    username = Some(rabbitUser),
    password = Some(rabbitPassword),
    requeueOnNack = false,
    internalQueueSize = Some(500)
  )

}

object DockerRabbit {

  val dockerImage         = "rabbitmq:alpine"
  val dockerContainerName = "fs2rabbit-docker"

  /** asserts that docker is available on host os **/
  def assertDockerAvailable(): Unit = {
    val r = "docker -v".!!
    println(s"Verifying docker is available: $r")
  }

  def startDocker(port: Int, user: String, password: String, virtualHost: String): String = {
    val dockerId = new java.util.concurrent.LinkedBlockingQueue[String](1)

    val removeCmd = s"docker rm -f $dockerContainerName"

    val runCmd =
      s"docker run --name $dockerContainerName -d -p $port:5672 " +
        s"-e RABBITMQ_DEFAULT_USER=$user -e RABBITMQ_DEFAULT_PASS=$password -e RABBITMQ_DEFAULT_VHOST=$virtualHost " +
        dockerImage

    val thread = new Thread(
      new Runnable {
        def run(): Unit = {
          removeCmd.!
          val result = runCmd.!!.trim

          var observer: Option[Process] = None

          val onMessage: String => Unit = str => {
            if (str.contains("Server startup complete")) {
              observer.foreach(_.destroy())
              dockerId.put(result)
            } else {
              println(str)
            }
          }

          val logger = ProcessLogger(onMessage, _ => ())

          println(s"Awaiting Docker startup ($dockerImage @ 127.0.0.1:$port)")
          val observeCmd = s"docker logs -f $result"
          observer = Some(observeCmd.run(logger))
        }
      },
      s"Docker $dockerImage startup observer"
    )
    thread.start()
    val id = dockerId.peek
    println(s"Docker ($dockerImage @ 127.0.0.1:$port) started successfully as $id ")
    id
  }

  def stopDocker(instance: String): Unit = {
    s"docker rm -f $instance".!!
    ()
  }

}
