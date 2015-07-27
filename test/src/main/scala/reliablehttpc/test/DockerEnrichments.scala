/*
 * Copyright 2015 the original author or authors.
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
package reliablehttpc.test

import com.github.dockerjava.api._
import com.github.dockerjava.api.command.CreateContainerCmd
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory

import scala.collection.convert.wrapAsScala._

object DockerEnrichments {
  lazy val logger = LoggerFactory.getLogger(getClass)

  implicit class DockerEnrichment(docker: DockerClient) {
    def containerFindByNameOrCreate(containerName: String, image: String)
                                   (prepareCreateCommand: CreateContainerCmd => CreateContainerCmd): String = {
      docker.listContainersCmd.withShowAll(true).exec().find(_.getNames.exists(_.contains(containerName))) match {
        case Some(container) =>
          logger.debug(s"Found container for name: $containerName: $container")
          container.getId
        case None =>
          logger.info(s"Not found container for name: $containerName. Creating for image: $image")
          val createWithNameCommand = docker.createContainerCmd(image).withName(containerName)
          val createResult = prepareCreateCommand(createWithNameCommand).exec()
          if (Option(createResult.getWarnings).exists(_.nonEmpty)) {
            logger.warn(createResult.getWarnings.mkString(s"Warnings during container create with name: $containerName for image: $image:\n", ",\n", ""))
          }
          createResult.getId
      }
    }

    def attachLogging(containerId: String) = {
      new Thread {
        override def run(): Unit = {
          val input = docker.logContainerCmd(containerId).withStdOut().withStdErr().withFollowStream().withTail(0).exec()
          IOUtils.copy(input, System.out)
        }
      }.start()
    }

    def stopContainerOnShutdown(containerId: String, secondsToWaitBeforeKilling: Int) = {
      Runtime.getRuntime.addShutdownHook(new Thread {
        override def run(): Unit = {
          logger.info(s"Graceful stopping container: $containerId")
          docker.stopContainerCmd(containerId).withTimeout(secondsToWaitBeforeKilling).exec()
          logger.info(s"Container shutdown successful: $containerId")
        }
      })
    }
  }
}