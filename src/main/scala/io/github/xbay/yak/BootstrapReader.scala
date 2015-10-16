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

class BootstrapReader(id: String, db: String, collection: String)
                    (implicit val system: ActorSystem, val timeout: Timeout) {
  val actor = system.actorOf(
    Props(new BootstrapReaderActor(db, collection)),
    "bootstrap-reader-" + id)

  def resume() = {
    actor ! "resume"
  }
}

class BootstrapReaderActor(val dbName: String, val collectionName: String) extends Actor {
  val collection = connect()

  def connect(): BSONCollection = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))
    val db = connection(dbName)
    db[BSONCollection](collectionName)
  }

  def receive  = {
    case "resume" => {
    }
  }
}
