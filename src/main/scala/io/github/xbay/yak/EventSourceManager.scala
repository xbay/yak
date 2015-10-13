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
  implicit val timeout = Timeout(10 seconds)

  val managerActor = system.actorOf(Props(new EventSourceManager()), "manager")

  object Models {
    //request or response
    case class CreateEventSourceRequest(db: String, collections: List[String])
    case class CreateEventSourceResponse(id: String)
    case class EventSourceResponse(id: String, db: String, collections: List[String])
    case class GetEventSourcesRequest()
    case class GetEventSourcesResponse(eventSources: List[EventSourceResponse])
    case class DeleteEventSourceRequest(id: String)
    case class DeleteEventSourceResponse(eventSource: Option[EventSourceResponse])

    //persist
    case class EventSourcePersist(id: String, db: String, collections: List[String])
    case class EventSourceTablePersist(table: Map[String, EventSourcePersist])
  }

  import Models._

  def createEventSource(request: CreateEventSourceRequest): Future[CreateEventSourceResponse] =
    managerActor.ask(request).mapTo[CreateEventSourceResponse]

  def getEventSources(): Future[GetEventSourcesResponse] =
    managerActor.ask(GetEventSourcesRequest()).mapTo[GetEventSourcesResponse]

  def deleteEventSource(request: DeleteEventSourceRequest): Future[DeleteEventSourceResponse] =
    (managerActor ? request).mapTo[DeleteEventSourceResponse]
}

class EventSourceManager (implicit system: ActorSystem, timeout: Timeout) extends PersistentActor {
  override def persistenceId = "event_source_manager"

  import EventSourceManager.Models._

  private val eventSourceTable = collection.mutable.Map[String, EventSource]()

  private def snapshot(): Unit = {
    saveSnapshot(EventSourceTablePersist((eventSourceTable.map{ item =>
      val id = item._1
      val eventSource = item._2
      (id, EventSourcePersist(
        eventSource.id,
        eventSource.db,
        eventSource.collections
      ))
    }).toMap))
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

    case request: GetEventSourcesRequest =>
      val response = GetEventSourcesResponse(eventSourcesList)
      sender ! response

    case request: DeleteEventSourceRequest =>
      val id = request.id
      if(eventSourceTable.contains(id))
        eventSourceTable -= id
      val response = DeleteEventSourceResponse(eventSourcesList.filter(item => item.id == id).headOption)
      sender ! response

    case SaveSnapshotSuccess(metadata)         => // ...

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
