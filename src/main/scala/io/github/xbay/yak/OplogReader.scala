package io.github.xbay.yak

import akka.actor.{ActorRef, Actor, ActorSystem, Props}
import akka.util.Timeout
import io.github.xbay.yak.OplogReader.Message.Resume
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.apache.commons.codec.binary.Base64

/**
 * Created by uni.x.bell on 10/14/15.
 */
object OplogReader {
  object Message {
    case class Resume(startId: Option[String])
  }
}

class OplogReader(id: String, db: String, collections: List[String])
                 (implicit val system: ActorSystem, val timeout: Timeout) {
  val actor = system.actorOf(
    Props(new OplogReaderActor(db, collections)),
    "bootstrap-reader-" + id)

  val scheduler = system.scheduler.schedule(50 milliseconds, 50 milliseconds, actor, "tick")

  def pause() = {
    actor ! "pause"
  }

  def resume(startId: Option[String]) = {
    actor ! Resume(startId)
  }
}

class OplogReaderActor(val dbName: String, val collectionNames: List[String]) extends Actor {
  var state = "pause"
  var lastId: Array[Byte] = Array('0')
  val collection = connect()

  def connect(): BSONCollection = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))
    val db = connection("local")
    db[BSONCollection]("oplog.rs")
  }

  def parseDoc(col: String, doc: BSONDocument): Option[Event] = {
    val op = doc.getAs[BSONString]("op").getOrElse(BSONString("")).value
    op match {
      case "i" => {
        val o = doc.getAs[BSONDocument]("o").getOrElse(BSONDocument())
        val idOption = o.getAs[BSONObjectID]("_id")
        idOption match {
          case Some(id) => Some(Event(id = id.stringify, collection = col, op = "create"))
          case None => None
        }
      }
      case "ur" => {
        val pk = doc.getAs[BSONDocument]("pk").getOrElse(BSONDocument())
        val idOption = pk.getAs[BSONObjectID]("")
        idOption match {
          case Some(id) => Some(Event(id = id.stringify, collection = col, op = "create"))
          case None => None
        }
      }
      case _ => None
    }

  }

  //FIXME: Only for tokumx
  def parseOplog(log: BSONDocument): List[Event] = {
    val ops = log.getAs[BSONArray]("ops")
    ops match {
      case Some(ops) => {
        ops.values.toList.map(op => {
          val opDoc = op.asInstanceOf[BSONDocument]
          val ns = opDoc.getAs[BSONString]("ns").getOrElse(BSONString("db.")).value.split("\\.")
          val db = if(ns.size == 2) ns(0) else ""
          val col = if(ns.size == 2) ns(1) else ""
          if(db == dbName && collectionNames.contains(col)) parseDoc(col, opDoc) else None
        }).filter(_.isDefined).map(_.get)
      }
      case None => List[Event]()
    }
  }

  def handleTick(parent: ActorRef) = {
    collection
      .find(BSONDocument(
      "_id" -> BSONDocument("$gt" -> BSONBinary(lastId, Subtype.GenericBinarySubtype))),
        BSONDocument("_id" -> 1))
      .cursor[BSONDocument]()
      .collect[List](EventSource.THRESHOLD)
      .map (logs => {
      if(!logs.isEmpty) {
        val lastLog = logs.last
        lastLog.getAs[BSONBinary]("_id") match {
          case Some(id) => lastId = id.byteArray
          case _ =>
        }
        val events = logs.map(log => {
          parseOplog(log)
        }).foldLeft(List[Event]())((a, b) => a ++ b)
        parent ! OplogEventFill(events, Some(Base64.encodeBase64String(lastId)))
      }
    })
  }

  def receive  = {
    case "pause" => state = "pause"
    case "tick" => {
      val parent = context.parent
      state match {
        case "resume" => handleTick(parent)
        case _ =>
      }
    }
    case Resume(startId) => {
      startId match {
        case Some(startId) => lastId = Base64.decodeBase64(startId)
        case None => lastId = Array('0')
      }
      state = "resume"
    }
  }
}
