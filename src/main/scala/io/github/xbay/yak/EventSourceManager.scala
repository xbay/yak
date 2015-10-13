package io.github.xbay.yak

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.persistence._

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
    //request or response
    case class CreateEventSourceRequest(db: String, collections: List[String])
    case class CreateEventSourceResponse(id: String)
    case class GetEventSourcesRequest()
    case class EventSourceResponse(id: String, db: String, collections: List[String])
    case class GetEventSourcesResponse(eventSources: List[EventSourceResponse])

    //persist
    case class EventSourcePersist(id: String, db: String, collections: List[String])
    case class EventSourceTablePersist(table: Map[String, EventSourcePersist])
  }

  import Models._

  def createEventSource(request: CreateEventSourceRequest): Future[CreateEventSourceResponse] = {
    managerActor.ask(request)(timeout).mapTo[CreateEventSourceResponse]
  }

  def getEventSources(): Future[GetEventSourcesResponse] = {
    managerActor.ask(GetEventSourcesRequest())(timeout).mapTo[GetEventSourcesResponse]
  }
}

class EventSourceManager (timeout: Timeout) extends PersistentActor {
  override def persistenceId = "event_source_manager"

  import EventSourceManager.Models._

  private val eventSourceTable = collection.mutable.Map[String, EventSource]()

  def receiveCommand = {
    case request: CreateEventSourceRequest => {
      val id = BSONObjectID.generate.stringify
      val eventSource = new EventSource(id, request.db, request.collections)
      eventSourceTable += ((id, eventSource))
      saveSnapshot(EventSourceTablePersist((eventSourceTable.map{ item =>
        val id = item._1
        val eventSource = item._2
        (id, EventSourcePersist(
          eventSource.id,
          eventSource.db,
          eventSource.collections
        ))
      }).toMap))
      sender() ! CreateEventSourceResponse(id)
    }
    case request: GetEventSourcesRequest => {
      val eventSources = eventSourceTable.toList.map(item => {
        val eventSource = item._2
        EventSourceResponse(eventSource.id, eventSource.db, eventSource.collections)
      })
      val response = GetEventSourcesResponse(eventSources)
      sender() ! response
    }
    case SaveSnapshotSuccess(metadata)         => // ...
    case SaveSnapshotFailure(metadata, reason) => // ...
  }

  def receiveRecover = {
    case SnapshotOffer(_, eventSourceTablePersist: EventSourceTablePersist) => {
      eventSourceTablePersist.table.map(item => {
        val id = item._1
        val eventSourcePersist = item._2
        val eventSource = new EventSource(
          eventSourcePersist.id,
          eventSourcePersist.db,
          eventSourcePersist.collections)
        eventSourceTable += ((eventSource.id, eventSource))
      })
    }
  }
}
