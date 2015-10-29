package io.github.xbay.yak

import akka.util.Timeout
import org.specs2.mutable.Specification
import scala.concurrent.duration._
import spray.testkit.Specs2RouteTest

class RestApiSpec extends Specification with Specs2RouteTest with RestRoutes {
  def actorRefFactory = system

  val executionContext = scala.concurrent.ExecutionContext.global
  val requestTimeout = Timeout(5, SECONDS)

  "Yak's Rest API" should {

    "return a event_source list" in {
      Get("event_source") ~> routes  ~> check {
        responseAs[String] must contain("eventSources")
      }
    }
  }
}
