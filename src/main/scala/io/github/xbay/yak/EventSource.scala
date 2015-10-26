package io.github.xbay.yak

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.persistence
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by uni.x.bell on 10/9/15.
 */
class EventSource(val id: String, val db: String, val collections: List[String])
                 (implicit system: ActorSystem, timeout: Timeout) {
  val actor = system.actorOf(Props(new EventSourceActor(id, db, collections)), "event-source-" + id)

  def fetch(size: Int): Future[List[Event]] = {
    actor.ask(EventFetchRequest(size)).mapTo[EventFetchResponse].map(_.events)
  }

  def expunge(size: Int): Future[Int] = {
    actor.ask(EventExpungeRequest(size)).mapTo[EventExpungeResponse].map(_.size)
  }
}

case class EventFetchRequest(size: Int)
case class EventFetchResponse(events: List[Event])

case class EventExpungeRequest(size: Int)
case class EventExpungeResponse(size: Int)

case class EventFill(ids: List[String], collection: String)

case class Event(id: String, collection: String, op: String)

case class BootstrapReaderState(id: String, recentRecordId: String, finished: Boolean)
case class OplogReaderState(id: String, recentRecordId: String)
case class EventSourceActorState(
  bootstrapActorStates: Map[String, BootstrapReaderState],
  oplogReaderState: OplogReaderState,
  stage: String)

class EventSourceActor(val id: String, db: String, collections: List[String])
                      (implicit system: ActorSystem, timeout: Timeout) extends PersistentActor {
  override def persistenceId = "event-source-" + id

  var events = List[Event]()
  var state = EventSourceActorState(
    collections.map(collection =>
      Tuple2(collection, new BootstrapReaderState(id, collection, false))
    ).toMap[String, BootstrapReaderState],
    OplogReaderState(id, ""),
    "boostrap")
  var bootstrapReaders: List[BootstrapReader] = List()
  /* collections.map(collection =>
    Tuple2(id, new BootstrapReader(id, db, collection))
  ).toMap[String, BootstrapReader]*/
  var oplogReader:Option[OplogReader] = None//new OplogReader(id, db, collections)

  def bootstrapReaderFromState() = {

  }

  private def snapshot(): Unit = saveSnapshot(state)

  def receiveCommand = {
    case EventFetchRequest(size) =>
      sender() ! EventFetchResponse(events.take(size))
    case EventExpungeRequest(size) =>
      val res = events.splitAt(size)
      events = res._2
      sender() ! EventExpungeResponse(res._1.size)
    case EventFill(ids, collection) =>
      events = events ++ ids.map(Event(_, collection, "create"))

    case SaveSnapshotSuccess(metadata) => // ...

    case SaveSnapshotFailure(metadata, reason) => // ...
  }

  def receiveRecover = {
    case SnapshotOffer(_, stateRecover: EventSourceActorState) => state = stateRecover
  }
}
