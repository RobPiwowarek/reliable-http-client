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
package rhttpc.sample

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import rhttpc.client.{SubscriptionRegistered, DoRegisterSubscription, SubscriptionManager, SubscriptionOnResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class InMemDelayedEchoClient(delay: FiniteDuration)(implicit system: ActorSystem) extends DelayedEchoClient {
  import system.dispatcher

  private val subOnMsg: collection.concurrent.Map[SubscriptionOnResponse, String] = collection.concurrent.TrieMap()

  override def requestResponse(msg: String)(implicit ec: ExecutionContext): Future[DoRegisterSubscription] = {
    val uniqueSubOnResponse = SubscriptionOnResponse(UUID.randomUUID().toString)
    subOnMsg.put(uniqueSubOnResponse, msg)
    Future.successful(DoRegisterSubscription(uniqueSubOnResponse))
  }

  val subscriptionManager: SubscriptionManager = new SubscriptionManager {
    override def register(subscription: SubscriptionOnResponse, consumer: ActorRef): Future[SubscriptionRegistered] = {
      system.scheduler.scheduleOnce(delay) {
        subOnMsg.remove(subscription).foreach { msg =>
          consumer ! msg
        }
      }
      Future.successful(SubscriptionRegistered(subscription))
    }

    override def run(): Unit = {}
  }
}