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

class EventSource (val id: String, val db: String, val collections: List[String])
                  (implicit system: ActorSystem, timeout: Timeout) {
  val actor = system.actorOf(Props(new EventSourceActor(id, db, collections)), "event-source-" + id)
}

case class EventFetch()
case class EventExpunge()
case class EventFill()

case class Event(id: String, collection: String, op: String)
case class EventSourceActorState(recentRecordId: Option[String], stage: String)

class EventSourceActor (val id: String, db: String, collections: List[String])
                       (implicit system: ActorSystem, timeout: Timeout) extends PersistentActor {
  override def persistenceId = "event-source-" + id

  var events = List[Event]()
  var state = EventSourceActorState(None, "boostrap")
  val bootstrapReaders = collections.map(collection =>
    Tuple2(id, new BootstrapReader(id, db, collection))
  ).toMap[String, BootstrapReader]
  val oplogReader = new OplogReader(id, db, collections)

  private def snapshot(): Unit = saveSnapshot(state)

  def receiveCommand = {
    case cmd: EventFetch =>
    case cmd: EventExpunge =>
    case cmd: EventFill =>

    case SaveSnapshotSuccess(metadata)         => // ...

    case SaveSnapshotFailure(metadata, reason) => // ...
  }

  def receiveRecover = {
    case SnapshotOffer(_, stateRecover: EventSourceActorState) => state = stateRecover
  }
}