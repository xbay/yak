package io.github.xbay.yak

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing._

/**
 * Created by uni.x.bell on 10/9/15.
 */
class RestApi(timeout: Timeout) extends HttpServiceActor
    with RestRoutes  {

  implicit val requestTimeout = timeout

  def receive = runRoute(routes)

  implicit def executionContext = context.dispatcher
}

trait RestRoutes extends HttpService
    with ModelMarshalling {

  import StatusCodes._
  import EventSourceManager.Models._

  implicit def executionContext: ExecutionContext
  implicit def requestTimeout: Timeout

  def routes: Route = eventSourceRoute ~ eventSourcesRoute

  def eventSourcesRoute = pathPrefix("event_source") {
    pathEndOrSingleSlash {
      get {
        // GET /event_source
        onSuccess(EventSourceManager.getEventSources()) { response =>
          complete(OK, response)
        }
      } ~
      post {
        // POST /event_source
        entity(as[CreateEventSourceRequest]) { request =>
          onSuccess(EventSourceManager.createEventSource(request)) { response =>
            complete(OK, response)
          }
        }
      }
    }
  }

  def eventSourceRoute = pathPrefix("event_source" / Segment) { eventSourceId =>
    pathEndOrSingleSlash {
      // DELETE /event_source/:id
      delete {
        onSuccess(EventSourceManager.deleteEventSource(DeleteEventSourceRequest(eventSourceId))) { response =>
          complete(OK, response)
        }
      }
    }
  }
}
