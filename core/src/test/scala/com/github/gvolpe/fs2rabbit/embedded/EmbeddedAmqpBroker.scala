/*
 * Copyright 2017 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.embedded

import java.io.File
import java.security.Principal

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.utils.Fs2Utils.evalF
import com.google.common.io.Files
import fs2._
import org.apache.qpid.server.configuration.updater.{TaskExecutor, TaskExecutorImpl}
import org.apache.qpid.server.logging.{EventLogger, LoggingMessageLogger, MessageLogger}
import org.apache.qpid.server.model.{JsonSystemConfigImpl, SystemConfig}
import org.apache.qpid.server.plugin.{PluggableFactoryLoader, SystemConfigFactory}

import scala.collection.JavaConverters._

/**
  * Embedded AMQP Broker for testing purposes
  * */
object EmbeddedAmqpBroker {

  def createBroker: Stream[IO, Unit] =
    Stream.bracket[IO, (File, SystemConfig[_]), Unit](acquireSystemConfig)(
      _  => evalF[IO, Unit](()),
      fs => {
        val (file, systemConfig) = fs
        shutdown(file, systemConfig).map(_ => ())
      }
    )

  private[EmbeddedAmqpBroker] def shutdown(workDir: File, systemConfig: SystemConfig[_]) = IO {
    systemConfig.close()
    workDir.delete
  }

  private[EmbeddedAmqpBroker] def acquireSystemConfig: IO[(File, SystemConfig[_])] = IO {
    val taskExecutor: TaskExecutor    = new TaskExecutorImpl
    val messageLogger: MessageLogger  = new LoggingMessageLogger
    val eventLogger: EventLogger      = new EventLogger
    eventLogger.setMessageLogger(messageLogger)

    val configFactoryLoader = new PluggableFactoryLoader(classOf[SystemConfigFactory[_ <: SystemConfig[_]]])
    val configFactory = configFactoryLoader.get(JsonSystemConfigImpl.SYSTEM_CONFIG_TYPE)
    val initialConfigurationUrl: String = getClass.getClassLoader.getResource("amqp-config.json").toExternalForm

    val workDir = Files.createTempDir()

    val context = Map(
      "qpid.work_dir"   -> workDir.getAbsolutePath,
      "qpid.amqp_port"  -> "45947",
      "qpid.broker.defaultPreferenceStoreAttributes" -> "{\"type\": \"Noop\"}}"
    )

    val attributes = Map(
      "initialConfigurationLocation" -> initialConfigurationUrl,
      "context"   -> context.asJava,
      "storePath" -> s"$${json:qpid.work_dir}$${file.separator}config.json",
      "startupLoggedToSystemOut" -> "false"
    )

    val systemConfig = configFactory.newInstance(taskExecutor, eventLogger, new Principal {
      override def getName = "system"
    }, attributes.asJava)

    systemConfig.open()
    (workDir, systemConfig)
  }

}
