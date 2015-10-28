package io.github.xbay.yak

import akka.actor.{ActorContext, ActorSystem, Props}
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by uni.x.bell on 10/9/15.
 */
class EventSource(val id: String, val db: String, val collections: List[String])
                 (implicit system: ActorSystem, context: ActorContext, timeout: Timeout) {
  import EventSource.Models._
  val actor = context.actorOf(Props(new EventSourceActor(id, db, collections)), "event-source-" + id)

  def fetch(req: EventFetchRequest): Future[EventFetchResponse] = {
    actor.ask(req).mapTo[EventFetchResponse]
  }

  def expunge(req: EventExpungeRequest): Future[EventExpungeResponse] = {
    actor.ask(req).mapTo[EventExpungeResponse]
  }
}

object EventSource {
  object Models {
    case class EventFetchRequest(id: String, size: Int)
    case class EventFetchResponse(events: List[Event])

    case class EventExpungeRequest(id: String, size: Int)
    case class EventExpungeResponse(size: Int)
  }
}
case class EventFill(ids: List[String], collection: String)

case class Event(id: String, collection: String, op: String)

case class BootstrapReaderState(id: String, collection: String, recentRecordId: Option[String], finished: Boolean)
case class OplogReaderState(id: String, recentRecordId: String)
case class EventSourceReaderState(bootstrapReaderStates: Map[String, BootstrapReaderState],
  oplogReaderState: OplogReaderState,
  stage: String)

class EventSourceActor(val id: String, db: String, collections: List[String])
                      (implicit system: ActorSystem, timeout: Timeout) extends PersistentActor {
  import EventSource.Models._
  override def persistenceId = "event-source-" + id

  val THRESHHOLD = 100

  var events = List[Event]()
  var state = EventSourceReaderState(
    collections.map(collection =>
      Tuple2(collection, new BootstrapReaderState(id, collection, None, false))
    ).toMap[String, BootstrapReaderState],
    OplogReaderState(id, ""),
    "bootstrap")

  val bootstrapReaders = collections.map(collection =>
    Tuple2(collection, new BootstrapReader(id, db, collection))
  ).toMap[String, BootstrapReader]

  var oplogReader = new OplogReader(id, db, collections)

  private def snapshot(): Unit = saveSnapshot(state)

  def receiveCommand = {
    case EventFetchRequest(_, size) =>
      sender() ! EventFetchResponse(events.take(size))

    case EventExpungeRequest(_, size) =>
      val res = events.splitAt(size)
      events = res._2
      sender() ! EventExpungeResponse(res._1.size)
      tryFillData()

    case EventFill(ids, collection) =>
      println("event fill")
      events = events ++ ids.map(Event(_, collection, "create"))

    case SaveSnapshotSuccess(metadata) => // ...

    case SaveSnapshotFailure(metadata, reason) => // ...

  }

  def receiveRecover = {
    case SnapshotOffer(_, stateRecover: EventSourceReaderState) =>
      state = stateRecover

    case RecoveryCompleted => tryFillData()
  }

  def tryFillData(): Unit = {
    state.stage match {
      case "bootstrap" => {
        state.bootstrapReaderStates.foreach { st =>
          val readerState = st._2
          bootstrapReaders.get(readerState.collection).get.resume(readerState.recentRecordId)
        }
      }
      case _ => {

      }
    }
  }
}
