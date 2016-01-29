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
package rhttpc.client

import java.util.UUID

import akka.actor._
import org.slf4j.LoggerFactory
import rhttpc.utils.Recovered
import Recovered._
import rhttpc.client.config.ConfigParser
import rhttpc.client.consume.MessageConsumerFactory
import rhttpc.client.protocol.{Correlated, Exchange}
import rhttpc.client.proxy.{FailureResponseHandleStrategyChooser, ReliableProxyFactory}
import rhttpc.client.subscription.{SubscriptionManager, SubscriptionManagerFactory, WithSubscriptionManager}
import rhttpc.transport.{PubSubTransport, Publisher, WithDelayedPublisher, WithInstantPublisher}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal

class ReliableClient[Request, SendResult](publisher: Publisher[Correlated[Request]],
                                          publicationHandler: PublicationHandler[SendResult],
                                          additionalStartAction: => Unit,
                                          additionalStopAction: => Future[Unit])
                                         (implicit ec: ExecutionContext) {

  private lazy val logger = LoggerFactory.getLogger(getClass)

  def start() = {
    additionalStartAction
    publisher.start()
  }

  def send(request: Request): SendResult = {
    val correlationId = UUID.randomUUID().toString
    val correlated = Correlated(request, correlationId)
    val publicationAckFuture = publisher.publish(correlated).map { _ =>
      logger.debug(s"Request: $correlationId successfully acknowledged")
    }.recoverWith {
      case NonFatal(ex) =>
        logger.error(s"Request: $correlationId acknowledgement failure", ex)
        Future.failed(NoAckException(request, ex))
    }
    publicationHandler.processPublicationAck(correlationId, publicationAckFuture)
  }

  def stop(): Future[Unit] = {
    recovered("stopping request publisher", publisher.stop())
    recoveredFuture("additional stop action", additionalStopAction)
  }
}

case class NoAckException(request: Any, cause: Throwable) extends Exception(s"No acknowledge for request: $request", cause)

case class ReliableClientFactory(implicit actorSystem: ActorSystem) {
  import actorSystem.dispatcher
  
  private lazy val config = ConfigParser.parse(actorSystem)
  
  def inOutWithSubscriptions[Request, Response](send: Correlated[Request] => Future[Response],
                                                batchSize: Int = config.batchSize,
                                                queuesPrefix: String = config.queuesPrefix,
                                                retryStrategy: FailureResponseHandleStrategyChooser = config.retryStrategy,
                                                additionalStartAction: => Unit = {},
                                                additionalStopAction: => Future[Unit] = Future.successful(Unit))
                                               (implicit requestResponseTransport: PubSubTransport[Correlated[Request], Correlated[Exchange[Request, Response]]] with WithDelayedPublisher,
                                                responseRequestTransport: PubSubTransport[Correlated[Exchange[Request, Response]], Correlated[Request]] with WithInstantPublisher): InOutReliableClient[Request] = {
    val proxy = ReliableProxyFactory().publishingResponses(
      send = send,
      batchSize = batchSize,
      queuesPrefix = queuesPrefix,
      retryStrategy = retryStrategy
    )
    val subMgr = SubscriptionManagerFactory().create[Request, Response](
      batchSize = batchSize,
      queuesPrefix = queuesPrefix
    )
    val requestPublisher = requestResponseTransport.publisher(prepareRequestPublisherQueueData(queuesPrefix))
    def startAdditional() = {
      additionalStartAction
      subMgr.start()
      proxy.start()
    }
    def stopAdditional = {
      recoveredFuture("stopping proxy", proxy.stop())
        .flatMap(_ => recoveredFuture("stopping subscription manager", subMgr.stop()))
        .flatMap(_ => recoveredFuture("additional stop action", additionalStopAction))
    }
    new ReliableClient(
      publisher = requestPublisher,
      publicationHandler = subMgr,
      additionalStartAction = startAdditional(),
      additionalStopAction = stopAdditional
    ) with WithSubscriptionManager {
      override def subscriptionManager: SubscriptionManager = subMgr
    }
  }

  def inOut[Request, Response](send: Correlated[Request] => Future[Response],
                               handleResponse: Exchange[Request, Response] => Future[Unit],
                               batchSize: Int = config.batchSize,
                               queuesPrefix: String = config.queuesPrefix,
                               retryStrategy: FailureResponseHandleStrategyChooser = config.retryStrategy,
                               additionalStartAction: => Unit = {},
                               additionalStopAction: => Future[Unit] = Future.successful(Unit))
                              (implicit requestPublisherTransport: PubSubTransport[Correlated[Request], Correlated[Exchange[Request, Response]]] with WithDelayedPublisher,
                               requestSubscriberTransport: PubSubTransport[Correlated[Exchange[Request, Response]], Correlated[Request]] with WithInstantPublisher): InOnlyReliableClient[Request] = {
    val proxy = ReliableProxyFactory().publishingResponses(
      send = send,
      batchSize = batchSize,
      queuesPrefix = queuesPrefix,
      retryStrategy = retryStrategy
    )
    val responseConsumer = MessageConsumerFactory().create[Request, Response](
      handleMessage = handleResponse,
      batchSize = batchSize,
      queuesPrefix = queuesPrefix
    )
    def startAdditional() = {
      additionalStartAction
      responseConsumer.start()
      proxy.start()
    }
    def stopAdditional = {
      recoveredFuture("stopping proxy", proxy.stop())
        .flatMap(_ => recoveredFuture("stopping response consumer", responseConsumer.stop()))
        .flatMap(_ => recoveredFuture("additional stop action", additionalStopAction))
    }
    create(
      publicationHandler = StraightforwardPublicationHandler,
      queuesPrefix = queuesPrefix,
      additionalStartAction = startAdditional(),
      additionalStopAction = stopAdditional
    )
  }

  def inOnly[Request](send: Correlated[Request] => Future[Unit],
                      batchSize: Int = config.batchSize,
                      queuesPrefix: String = config.queuesPrefix,
                      retryStrategy: FailureResponseHandleStrategyChooser = config.retryStrategy,
                      additionalStartAction: => Unit = {},
                      additionalStopAction: => Future[Unit] = Future.successful(Unit))
                     (implicit requestPublisherTransport: PubSubTransport[Correlated[Request], Any] with WithDelayedPublisher,
                      requestSubscriberTransport: PubSubTransport[Nothing, Correlated[Request]] with WithInstantPublisher): InOnlyReliableClient[Request] = {
    val proxy = ReliableProxyFactory().skippingResponses(
      send = send,
      batchSize = batchSize,
      queuesPrefix = queuesPrefix,
      retryStrategy = retryStrategy
    )
    def startAdditional() = {
      additionalStartAction
      proxy.start()
    }
    def stopAdditional = {
      recoveredFuture("stopping proxy", proxy.stop())
        .flatMap(_ => recoveredFuture("additional stop action", additionalStopAction))
    }
    create(
      publicationHandler = StraightforwardPublicationHandler,
      queuesPrefix = queuesPrefix,
      additionalStartAction = startAdditional(),
      additionalStopAction = stopAdditional
    )
  }

  def create[Request, SendResult](publicationHandler: PublicationHandler[SendResult],
                                  queuesPrefix: String = config.queuesPrefix,
                                  additionalStartAction: => Unit = {},
                                  additionalStopAction: => Future[Unit] = Future.successful(Unit))
                                 (implicit transport: PubSubTransport[Correlated[Request], _] with WithDelayedPublisher): ReliableClient[Request, SendResult] = {
    val requestPublisher = transport.publisher(prepareRequestPublisherQueueData(queuesPrefix))
    new ReliableClient(
      publisher = requestPublisher,
      publicationHandler = publicationHandler,
      additionalStartAction = additionalStartAction,
      additionalStopAction = additionalStopAction
    )
  }

}