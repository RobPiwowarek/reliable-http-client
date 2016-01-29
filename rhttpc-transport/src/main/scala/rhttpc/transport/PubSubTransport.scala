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
package rhttpc.transport

import akka.actor.ActorRef

import scala.concurrent.Future
import scala.language.{higherKinds, postfixOps}
import scala.util.Try

trait PubSubTransport[-PubMsg, +SubMsg] {
  def publisher(queueName: String): Publisher[PubMsg] =
    publisher(OutboundQueueData(queueName))

  def subscriber(queueName: String, consumer: ActorRef): Subscriber[SubMsg] =
    subscriber(InboundQueueData(queueName, batchSize = 10), consumer)

  def publisher(queueData: OutboundQueueData): Publisher[PubMsg]

  def subscriber(queueData: InboundQueueData, consumer: ActorRef): Subscriber[SubMsg]

  def fullMessageSubscriber(queueData: InboundQueueData, consumer: ActorRef): Subscriber[SubMsg]

}

trait WithInstantPublisher { self: PubSubTransport[_, _] =>
}

trait WithDelayedPublisher { self: PubSubTransport[_, _] =>
}

case class InboundQueueData(name: String, batchSize: Int, durability: Boolean = true, autoDelete: Boolean = false)

case class OutboundQueueData(name: String, durability: Boolean = true, autoDelete: Boolean = false, delayed: Boolean = false)

trait Publisher[-Msg] {

  final def publish(msg: Msg): Future[Unit] =
    publish(Message(msg))

  def publish(msg: Message[Msg]): Future[Unit]

  def start(): Unit

  def stop(): Future[Unit]

}

trait Subscriber[+SubMsg] {

  def start(): Unit

  def stop(): Future[Unit]
}

trait RejectingMessage { self: Exception =>
}

trait Serializer[-PubMsg] {
  def serialize(obj: PubMsg): String
}

trait Deserializer[+SubMsg] {
  def deserialize(value: String): Try[SubMsg]
}