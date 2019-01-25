package com.github.gvolpe.fs2rabbit

import cats.effect.{ContextShift, IO}
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.{ExecutionContext, SyncVar}
import scala.sys.process._

trait DockerRabbit extends BeforeAndAfterAll { self: Suite =>

  import DockerRabbit._

  // override this if the Redis container has to be started before invocation
  // when developing tests, this likely shall be false, so there is no additional overhead starting Redis
  protected lazy val startContainers: Boolean = true

  protected lazy val rabbitPort: Int           = 15672
  protected lazy val rabbitUser: String        = "admin"
  protected lazy val rabbitPassword: String    = "admin"
  protected lazy val rabbitVirtualHost: String = "test"

  private var dockerInstanceId: Option[String] = None

  implicit val cts: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

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

  protected lazy val rabbitConfig = Fs2RabbitConfig(
    host = "localhost",
    port = rabbitPort,
    virtualHost = rabbitVirtualHost,
    connectionTimeout = 5,
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
    val r = Process("docker -v").!!
    println(s"Verifying docker is available: $r")
  }

  def startDocker(port: Int, admin: String, password: String, virtualHost: String): String = {
    val dockerId = new SyncVar[String]()

    val removeCmd = s"docker rm -f $dockerContainerName"

    val runCmd =
      s"docker run --name $dockerContainerName -d -p $port:5672 " +
        s"-e RABBITMQ_DEFAULT_USER=$admin -e RABBITMQ_DEFAULT_PASS=$password -e RABBITMQ_DEFAULT_VHOST=$virtualHost " +
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
            }
          }

          val logger = ProcessLogger(onMessage, _ => ())

          println(s"Awaiting Docker startup ($dockerImage @ 127.0.0.1:$port)")
          val observeCmd = s"docker logs -f $result"
          observer = Some(Process(observeCmd).run(logger))
        }
      },
      s"Docker $dockerImage startup observer"
    )
    thread.start()
    val id = dockerId.get
    println(s"Docker ($dockerImage @ 127.0.0.1:$port) started successfully as $id ")
    id
  }

  def stopDocker(instance: String): Unit = {
    s"docker rm -f $instance".!!
    ()
  }

}
