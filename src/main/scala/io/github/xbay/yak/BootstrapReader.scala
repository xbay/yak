package io.github.xbay.yak

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._
import scala.concurrent.ExecutionContext.Implicits.global

case class Resume(id: String)

case class BootstrapResultModel(id: String)

object BootstrapResultModel {
  implicit object Reader extends BSONDocumentReader[BootstrapResultModel] {
    def read(doc: BSONDocument): BootstrapResultModel = {
      BootstrapResultModel(doc.getAs[BSONObjectID]("_id").get.stringify)
    }
  }
}

/**
 * Created by uni.x.bell on 10/14/15.
 */
class BootstrapReader(id: String, db: String, collection: String)
                     (implicit system: ActorSystem, context: ActorContext, val timeout: Timeout) {
  val actor = context.actorOf(
    Props(new BootstrapReaderActor(db, collection)),
    "bootstrap-reader-" + id + "-" + collection)

  def resume(id: Option[String]) = {
    actor ! Resume(id.getOrElse("0" * 24))
  }
}

class BootstrapReaderActor(val dbName: String, val collectionName: String) extends Actor {
  val collection: BSONCollection = connect()

  def connect(): BSONCollection = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))
    val db = connection(dbName)
    db[BSONCollection](collectionName)
  }

  def receive = {
    case Resume(id) =>
      val parent = context.parent
      println("resume")
      collection
        .find(BSONDocument(  //query
            "_id" -> BSONDocument("$gt" -> BSONObjectID(id))),
          BSONDocument("_id" -> 1)) //field filter
        .cursor[BootstrapResultModel]()
        .collect[List](100).map{ids =>
        println(ids.size)
        println(parent.path)
        parent ! EventFill(ids.map(_.id), collectionName)
      }
  }
}
