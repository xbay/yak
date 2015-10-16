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

class OplogReader(id: String, db: String, collections: List[String])
                 (implicit val system: ActorSystem, val timeout: Timeout) {
  val actor = system.actorOf(
    Props(new OplogReaderActor(db, collections)),
    "bootstrap-reader-" + id)

  def pause() = {
    actor ! "pause"
  }

  def resume() = {
    actor ! "resume"
  }
}

class OplogReaderActor(val dbName: String, val collectionNames: List[String]) extends Actor {
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
