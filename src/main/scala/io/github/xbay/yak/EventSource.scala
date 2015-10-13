package io.github.xbay.yak

/**
 * Created by uni.x.bell on 10/9/15.
 */

class EventSource (val id: String, val db: String, val collections: List[String]) {
  var lastId: Option[String] = None
}
