package io.github.xbay.yak

import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.api.collections.bson.BSONCollection
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by uni.x.bell on 10/14/15.
 */

class OplogReader(id: String, db: String, collection: String)
                 (implicit val system: ActorSystem, val timeout: Timeout) extends EventReader {
  val actor = system.actorOf(
    Props(new OplogReaderActor(db, collection)),
    "bootstrap-reader-" + id)

  def pause() = {

  }

  def resume() = {

  }
}

class OplogReaderActor(val dbName: String, val collectionName: String) extends Actor {
  var state = "pause"
  val collection = connect()

  def connect(): BSONCollection = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))
    val db = connection(dbName)
    db[BSONCollection]("oplog")
  }

  def receive  = {
    case "pause" => state = "pause"
    case "resume" => {
      state = "resume"
    }
  }
}