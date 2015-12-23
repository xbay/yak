package io.github.xbay.yak

import akka.actor.{ActorContext, ActorSystem, Props}
import akka.event.slf4j.Logger
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout

import scala.concurrent.Future

/**
 * Created by uni.x.bell on 10/9/15.
 */
class EventSource(val id: String, val db: String, val collections: List[String])
                 (implicit system: ActorSystem, context: ActorContext, timeout: Timeout) {

  import EventSource.Messages._

  val actor = context.actorOf(Props(new EventSourceActor(id, db, collections)), "event-source-" + id)

  def fetch(req: EventFetchRequest): Future[EventFetchResponse] = {
    actor.ask(req).mapTo[EventFetchResponse]
  }

  def expunge(req: EventExpungeRequest): Future[EventExpungeResponse] = {
    actor.ask(req).mapTo[EventExpungeResponse]
  }
}

object EventSource {
  val THRESHOLD = 100

  object Messages {
    case class EventFetchRequest(id: String, size: Int)
    case class EventFetchResponse(events: List[Event])
    case class EventExpungeRequest(id: String, size: Int)
    case class EventExpungeResponse(size: Int)
  }

  object StateModels {
    sealed trait State
    case object Bootstrap extends State {
      override def toString = "bootstrap"
    }

    case object Oplog extends State {
      override def toString = "oplog"
    }
  }
}

case class Event(id: String, collection: String, op: String)

case class BootstrapEventFill(ids: List[String], collection: String, op: String, lastId: Option[String])
case class OplogEventFill(events: List[Event], lastId: Option[String])

case class BootstrapReaderState(
  id: String, //reader id
  collection: String, // collection name
  recentRecordId: Option[String], // remain id
  finished: Boolean //bootstrap finish flag
)

case class OplogReaderState(id: String, lastId: Option[String])

case class EventSourceReaderState(
  bootstrapReaderStates: Map[String, BootstrapReaderState],
  oplogReaderState: OplogReaderState,
  stage: EventSource.StateModels.State)

class EventSourceActor(val id: String, db: String, collections: List[String])
                      (implicit system: ActorSystem, timeout: Timeout) extends PersistentActor {

  import EventSource.Messages._
  import EventSource.StateModels._
  import BootstrapReader.Message._

  override def persistenceId = "event-source-" + id

  val logger = Logger(this.getClass, persistenceId)

  var events = List[Event]()
  var state = EventSourceReaderState(
    collections.map(c => (c, new BootstrapReaderState(id, c, None, false)))
      .toMap[String, BootstrapReaderState],
    OplogReaderState(id, None),
    Bootstrap)

  logger.info("xxxxxxxxxxxxxxxx")

  val bootstrapReaders = collections.map(c => (c, new BootstrapReader(id, db, c)))
    .toMap[String, BootstrapReader]

  var oplogReader = new OplogReader(id, db, collections)

  private def snapshot(): Unit = saveSnapshot(state)

  def receiveCommand = {
    //Messages from EventSourceManager------------------------
    case EventFetchRequest(_, size) =>
      sender() ! EventFetchResponse(events.take(size))

    case EventExpungeRequest(_, size) =>
      val res = events.splitAt(size)
      events = res._2
      sender() ! EventExpungeResponse(res._1.size)
      tryFillData()

    //Messages from Reader
    case BootstrapEventFill(ids, collection, op, lastId) =>
      println("bootstrap event fill")
      state.stage match {
        case Bootstrap => {
          state = state.copy(
            bootstrapReaderStates = state.bootstrapReaderStates.get(collection) match {
              case Some(bootstrapReaderState) => {
                state.bootstrapReaderStates ++ Map(
                  collection -> bootstrapReaderState.copy(recentRecordId = lastId))
              }
              case _ => state.bootstrapReaderStates
            }
          )
          events = events ++ ids.map(Event(_, collection, op))
          snapshot()
        }
        case _ =>
      }

    case OplogEventFill(evts, lastId) =>
      println("bootstrap event fill")
      state.stage match {
        case Oplog => {
          state = state.copy(
            oplogReaderState = state.oplogReaderState.copy(lastId = lastId)
          )
          events = events ++ evts
          snapshot()
        }

        case _ =>
      }

    case BootstrapFinish(collection) =>
      logger.info(s"Bootstrap finished by id: $id")
      val finishedState = state.bootstrapReaderStates.get(collection).get.copy(finished = true)
      state = state.copy(bootstrapReaderStates = state.bootstrapReaderStates + (collection -> finishedState))
      snapshot()

    //Messages from system
    case SaveSnapshotSuccess(metadata) => // ...

    case SaveSnapshotFailure(metadata, reason) => // ...

  }

  def receiveRecover = {
    case SnapshotOffer(_, stateRecover: EventSourceReaderState) =>
      logger.info("yyyyyyyyyyyyyyyyyyyyy")
      state = stateRecover

    case RecoveryCompleted => tryFillData()
  }

  def tryFillData(): Unit = {
    state.stage match {
      case Bootstrap => {
        state.bootstrapReaderStates.values.foreach { st =>
          st.finished match {
            case true =>
              //switch to Oplog stage
              state = state.copy(stage = Oplog)
              snapshot()
            case false =>
              bootstrapReaders.get(st.collection).get.resume(st.recentRecordId)
          }
        }
      }
      case Oplog => oplogReader.resume(state.oplogReaderState.lastId)
    }
  }
}
