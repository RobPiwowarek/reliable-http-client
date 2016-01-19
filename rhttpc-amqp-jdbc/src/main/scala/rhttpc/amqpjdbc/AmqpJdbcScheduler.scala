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
package rhttpc.amqpjdbc

import akka.actor.{Scheduler, Cancellable}
import akka.pattern._
import org.slf4j.LoggerFactory
import rhttpc.transport.{Serializer, Deserializer, Publisher, Message}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.{Try, Failure, Success}

private[amqpjdbc] trait AmqpJdbcScheduler[PubMsg] {

  def schedule(msg: Message[PubMsg], delay: FiniteDuration): Future[Unit]

  def start(): Unit

  def stop(): Unit

}

private[amqpjdbc] class AmqpJdbcSchedulerImpl[PubMsg](scheduler: Scheduler,
                                                      checkInterval: FiniteDuration,
                                                      repo: ScheduledMessagesRepository,
                                                      queueName: String,
                                                      batchSize: Int,
                                                      publisher: Publisher[PubMsg],
                                                      serializer: Serializer[Message[PubMsg]],
                                                      deserializer: Deserializer[Message[PubMsg]],
                                                      onCountChange: Int => Unit)
                                                     (implicit ec: ExecutionContext) extends AmqpJdbcScheduler[PubMsg] {
  private val logger = LoggerFactory.getLogger(getClass)

  @volatile private var scheduledCheck: Option[Cancellable] = None

  override def schedule(msg: Message[PubMsg], delay: FiniteDuration): Future[Unit] = {
    val serialized = serializer.serialize(msg)
    val saveFuture = repo.save(MessageToSchedule(queueName, serialized, delay))
    saveFuture.onSuccess {
      case _ => onCountChange(+1)
    }
    saveFuture
  }

  override def start(): Unit = {
    publishFetchedMessages
  }

  private def publishFetchedMessages: Future[Unit] = {
    val publishedFetchedFuture = repo.fetchMessagesShouldByRun(queueName, batchSize) { messages =>
      if (messages.nonEmpty) {
        logger.debug(s"Fetched ${messages.size}, publishing")
      }
      val handlingFutures = messages.map { message =>
        val tryDeserialized = deserializer.deserialize(message.message)
        tryDeserialized match {
          case Success(deseralized) =>
            publisher.publish(deseralized)
          case Failure(ex) =>
            logger.error(s"Message ${message.message} skipped because of parse failure", ex)
            Future.successful(())
        }
      }
      Future.sequence(handlingFutures)
    }
    publishedFetchedFuture.onSuccess {
      case fetchedCount if fetchedCount > 0 =>
        onCountChange(-fetchedCount)
    }
    publishedFetchedFuture.onComplete { _ =>
      scheduledCheck = Some(scheduler.scheduleOnce(checkInterval)(publishFetchedMessages))
    }
    publishedFetchedFuture.map(_ => Unit)
  }

  override def stop(): Unit = {
    scheduledCheck.foreach(_.cancel())
  }

}