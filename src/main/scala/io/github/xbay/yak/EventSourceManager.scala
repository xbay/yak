package io.github.xbay.yak

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by uni.x.bell on 10/9/15.
 */

object EventSourceManager {
  implicit val system = ActorSystem("yak")
  val timeout = 10 seconds

  val managerActor = system.actorOf(Props(new EventSourceManager(timeout)), "manager")

  object Models {
    case class CreateEventSourceRequest(db: String, collections: List[String])
    case class CreateEventSourceResponse(id: String)
    case class GetEventSourcesRequest()
    case class EventSourceResponse(id: String, db: String, collections: List[String])
    case class GetEventSourcesResponse(eventSources: List[EventSourceResponse])
  }

  import Models._

  def createEventSource(request: CreateEventSourceRequest): Future[CreateEventSourceResponse] = {
    managerActor.ask(request)(timeout).mapTo[CreateEventSourceResponse]
  }

  def getEventSources(): Future[GetEventSourcesResponse] = {
    managerActor.ask(GetEventSourcesRequest())(timeout).mapTo[GetEventSourcesResponse]
  }
}

class EventSourceManager (timeout: Timeout) extends Actor {
  import EventSourceManager.Models._

  private val eventSourceTable = collection.mutable.Map[String, EventSource]()

  def createSource(request: CreateEventSourceRequest): CreateEventSourceResponse = {
    val id = BSONObjectID.generate.stringify
    val eventSource = new EventSource(id, request.db, request.collections)
    eventSourceTable += ((id, eventSource))

    CreateEventSourceResponse(id)
  }

  def receive = {
    case request: CreateEventSourceRequest => {
      val response = createSource(request)
      sender() ! response
    }
    case request: GetEventSourcesRequest => {
      val eventSources = eventSourceTable.toList.map(item => {
        val eventSource = item._2
        EventSourceResponse(eventSource.id, eventSource.db, eventSource.collections)
      })
      val response = GetEventSourcesResponse(eventSources)
      sender() ! response
    }
  }
}
