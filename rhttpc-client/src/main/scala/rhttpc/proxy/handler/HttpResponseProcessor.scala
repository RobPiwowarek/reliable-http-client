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
package rhttpc.proxy.handler

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import rhttpc.proxy.HttpProxyContext
import rhttpc.transport.Publisher
import rhttpc.transport.protocol.Correlated

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

trait HttpResponseProcessor {
  // return future of ack
  def processResponse(response: Try[HttpResponse], ctx: HttpProxyContext): Future[Unit]
}

object AckingProcessor extends HttpResponseProcessor {
  override def processResponse(response: Try[HttpResponse], ctx: HttpProxyContext): Future[Unit] =
    AckAction()
}

object AckAction {
  def apply(): Future[Unit] = {
    Future.successful(Unit)
  }
}

case class RetryAction(publisher: Publisher[Correlated[HttpRequest]], ctx: HttpProxyContext) {
  def apply(request: Correlated[HttpRequest], delay: FiniteDuration): Future[Unit] = {
    val ackFuture = publisher.publishDelayed(request, delay)
    import ctx.executionContext
    ackFuture.onComplete {
      case Success(_) => ctx.log.debug(s"Publishing of delayed by $delay request for ${ctx.correlationId} successfully acknowledged")
      case Failure(ex) => ctx.log.error(s"Publishing of delayed by $delay request for ${ctx.correlationId} acknowledgement failed", ex)
    }
    ackFuture
  }
}

case class PublishAckAction(publisher: Publisher[Correlated[Try[HttpResponse]]], ctx: HttpProxyContext) {
  def apply(response: Try[HttpResponse]): Future[Unit] = {
    val ackFuture = publisher.publish(Correlated(response, ctx.correlationId))
    import ctx.executionContext
    ackFuture.onComplete {
      case Success(_) => ctx.log.debug(s"Publishing of message for ${ctx.correlationId} successfully acknowledged")
      case Failure(ex) => ctx.log.error(s"Publishing of message for ${ctx.correlationId} acknowledgement failed", ex)
    }
    ackFuture
  }
}