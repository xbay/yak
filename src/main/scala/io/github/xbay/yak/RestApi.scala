package io.github.xbay.yak

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Future}
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
      delete {
        // DELETE /event_source/:id
        onSuccess(
          EventSourceManager.deleteEventSource(
            DeleteEventSourceRequest(eventSourceId))) { response =>
          complete(OK, response)
        }
      }
    }
  }

  def eventSourceOpRoute = pathPrefix("event_source" / Segment / "events") { eventSourceId =>
    pathEndOrSingleSlash {
      delete {
        // DELETE /event_source/:id/events
        onSuccess(
          EventSourceManager.deleteEventSource(
            DeleteEventSourceRequest(eventSourceId))) { response =>
          complete(OK, response)
        }
      }
    }
  }
}
