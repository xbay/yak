package io.github.xbay.yak

import akka.actor._
import akka.event.slf4j.Logger
import akka.util.Timeout
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.ExecutionContext.Implicits.global

object BootstrapReader {

  object Message {

    case class Resume(startId: Option[String])

    case class BootstrapFinish(collection: String)

  }

}

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

  def resume(id: Option[String] = None) = {
    import BootstrapReader.Message._
    actor ! Resume(id)
  }
}

class BootstrapReaderActor(val dbName: String, val collectionName: String) extends Actor {
  import BootstrapReader.Message._

  val logger = Logger(this.getClass, collectionName)

  val collection: BSONCollection = connect()
  var lastId: String = "0" * 24

  def connect(): BSONCollection = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))
    val db = connection(dbName)
    db[BSONCollection](collectionName)
  }

  def receive = {
    case Resume(startId) =>
      lastId = startId.getOrElse(lastId)
      val parent = context.parent
      logger.debug("resume")
      collection
        .find(BSONDocument(  //query
            "_id" -> BSONDocument("$gt" -> BSONObjectID(lastId))),
          BSONDocument("_id" -> 1)) //field filter
        .cursor[BootstrapResultModel]()
        .collect[List](EventSource.THRESHOLD)
        .map { ids =>
          //Send Finish message if reads empty list
          ids.isEmpty match {
            case true =>
              parent ! BootstrapFinish(collectionName)
            case false =>
              parent ! EventFill(ids.map(_.id), collectionName)
              lastId = ids.last.id
          }
        }
  }
}
