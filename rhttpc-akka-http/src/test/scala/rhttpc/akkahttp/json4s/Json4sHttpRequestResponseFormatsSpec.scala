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
package rhttpc.akkahttp.json4s

import java.time.{Duration, Instant}
import java.util.UUID

import akka.http.scaladsl.model._
import org.json4s.native.Serialization
import org.scalatest._
import org.scalatest.prop.TableDrivenPropertyChecks
import rhttpc.client.protocol.{Correlated, WithRetryingHistory}
import rhttpc.client.proxy.{ExhaustedRetry, NonSuccessResponse}

import scala.util.{Failure, Success, Try}

class Json4sHttpRequestResponseFormatsSpec extends FlatSpec with TableDrivenPropertyChecks with Matchers {
  implicit val formats = Json4sHttpRequestResponseFormats.formats

  val requestsData = Table[WithRetryingHistory[Correlated[HttpRequest]]](
    "request",
    WithRetryingHistory.firstAttempt(
      Correlated(HttpRequest().withMethod(HttpMethods.POST).withEntity("foo"), UUID.randomUUID().toString),
      Instant.now
    ).withNextAttempt(Instant.now.plusSeconds(5), Duration.ofSeconds(10))
  )

  it should "work round-trip for requests" in {
    forAll(requestsData) { request =>
      val serialized = Serialization.writePretty(request)
//      println("Serialized: " + serialized)
      withClue("Serialized: " + serialized) {
        val deserialized = Serialization.read[WithRetryingHistory[Correlated[HttpRequest]]](serialized)
        deserialized shouldEqual request
      }
    }
  }

  val responsesData = Table[Correlated[Try[HttpResponse]]](
    "responses",
    Correlated(Success(HttpResponse().withEntity("bar")), UUID.randomUUID().toString),
    Correlated(Failure(ExhaustedRetry(NonSuccessResponse)), UUID.randomUUID().toString)
  )
  
  it should "work round-trip for responses" in {
    forAll(responsesData) { response =>
      val serialized = Serialization.writePretty(response)
//      println("Serialized: " + serialized)
      withClue("Serialized: " + serialized) {
        val deserialized = Serialization.read[Correlated[Try[HttpResponse]]](serialized)
        deserialized shouldEqual response
      }
    }
  }
}