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
package rhttpc.client.proxy

import scala.concurrent.duration._

trait FailureResponseHandleStrategyChooser {
  def choose(currentRetryAttempt: Int, lastPlannedDelay: Option[FiniteDuration]): ResponseHandleStrategy
}

sealed trait ResponseHandleStrategy

case class Retry(delay: FiniteDuration) extends ResponseHandleStrategy
case object SendToDLQ extends ResponseHandleStrategy
case object Handle extends ResponseHandleStrategy
case object Skip extends ResponseHandleStrategy

object HandleAll extends FailureResponseHandleStrategyChooser {
  override def choose(currentRetryAttempt: Int, lastPlannedDelay: Option[FiniteDuration]): ResponseHandleStrategy = Handle
}

object SkipAll extends FailureResponseHandleStrategyChooser {
  override def choose(currentRetryAttempt: Int, lastPlannedDelay: Option[FiniteDuration]): ResponseHandleStrategy = Skip
}

case class BackoffRetry(initialDelay: FiniteDuration, multiplier: BigDecimal, maxRetries: Int) extends FailureResponseHandleStrategyChooser {
  override def choose(currentRetryAttempt: Int, lastPlannedDelay: Option[FiniteDuration]): ResponseHandleStrategy = {
    if (currentRetryAttempt >= maxRetries) {
      SendToDLQ
    } else if (currentRetryAttempt == 1) {
      Retry(initialDelay)
    } else {
      val nextDelay = lastPlannedDelay match {
        case Some(lastDelay) =>
          (lastDelay.toMillis * multiplier).toLong.millis
        case None =>
          initialDelay.toMillis.millis
      }
      Retry(nextDelay)
    }
  }
}
