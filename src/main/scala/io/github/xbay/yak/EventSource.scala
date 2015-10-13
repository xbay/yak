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
  var lastId: Option[String] = None
}
