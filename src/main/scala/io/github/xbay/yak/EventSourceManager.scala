package io.github.xbay.yak

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import akka.util.Timeout
import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Created by uni.x.bell on 10/9/15.
 */
object EventSourceManager {
  implicit val system = ActorSystem("yak")
  implicit val timeout = Timeout(10 seconds)

  val managerActor = system.actorOf(Props(new EventSourceManager()), "manager")

  object Models {
    //request or response
    case class CreateEventSourceRequest(db: String, collections: List[String])
    case class CreateEventSourceResponse(id: String)
    case class EventSourceResponse(id: String, db: String, collections: List[String])
    case object GetEventSourcesRequest
    case class GetEventSourcesResponse(eventSources: List[EventSourceResponse])
    case class DeleteEventSourceRequest(id: String)
    case class DeleteEventSourceResponse(success: Boolean, reason: String = "")

    //persist
    case class EventSourcePersist(id: String, db: String, collections: List[String])
    case class EventSourceTablePersist(table: Map[String, EventSourcePersist])
  }

  import Models._
  import EventSource.Models._

  def createEventSource(request: CreateEventSourceRequest): Future[CreateEventSourceResponse] =
    managerActor.ask(request).mapTo[CreateEventSourceResponse]

  def getEventSources(): Future[GetEventSourcesResponse] =
    managerActor.ask(GetEventSourcesRequest).mapTo[GetEventSourcesResponse]

  def deleteEventSource(request: DeleteEventSourceRequest): Future[DeleteEventSourceResponse] =
    (managerActor ? request).mapTo[DeleteEventSourceResponse]

  def fetchEvents(request: EventFetchRequest): Future[EventFetchResponse] =
    managerActor.ask(EventFetchRequest).mapTo[EventFetchResponse]
}

class EventSourceManager (implicit system: ActorSystem, timeout: Timeout) extends PersistentActor {
  override def persistenceId = "event_source_manager"

  import EventSourceManager.Models._
  import EventSource.Models._

  private val eventSourceTable = collection.mutable.Map[String, EventSource]()

  private def snapshot(): Unit = {
    saveSnapshot(EventSourceTablePersist(eventSourceTable.map{ item =>
      val id = item._1
      val eventSource = item._2
      (id, EventSourcePersist(
        eventSource.id,
        eventSource.db,
        eventSource.collections
      ))
    }.toMap))
  }

  private def eventSourcesList: List[EventSourceResponse] =
    eventSourceTable.map(item => {
      val eventSource = item._2
      EventSourceResponse(eventSource.id, eventSource.db, eventSource.collections)
    }).toList

  def createEventSource(id: String, db: String, collections: List[String]): EventSource = {
    val eventSource = new EventSource(id, db, collections)
    eventSourceTable += ((id, eventSource))
    eventSource
  }

  def receiveCommand = {
    case request: CreateEventSourceRequest =>
      val id = BSONObjectID.generate.stringify
      createEventSource(id, request.db, request.collections)
      snapshot()
      sender ! CreateEventSourceResponse(id)

    case GetEventSourcesRequest =>
      val response = GetEventSourcesResponse(eventSourcesList)
      sender ! response

    case req: EventFetchRequest =>
      val eventSource = eventSourceTable.get(req.id).get
      eventSource.fetch(req) map { res =>
        sender ! res
      }

    case request: DeleteEventSourceRequest =>
      val id = request.id
      if(eventSourceTable.contains(id)) {
        eventSourceTable -= id
        snapshot()
        sender ! DeleteEventSourceResponse(true)
      } else {
        sender ! DeleteEventSourceResponse(false, "not exist")
      }

    case SaveSnapshotSuccess(metadata) => // ...

    case SaveSnapshotFailure(metadata, reason) => // ...
  }

  def receiveRecover = {
    case SnapshotOffer(_, eventSourceTablePersist: EventSourceTablePersist) =>
      eventSourceTablePersist.table.map(item => {
        val id = item._1
        val eventSourcePersist = item._2
        createEventSource(eventSourcePersist.id, eventSourcePersist.db, eventSourcePersist.collections)
      })
  }
}
