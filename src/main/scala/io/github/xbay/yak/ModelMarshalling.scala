package io.github.xbay.yak

import spray.json.DefaultJsonProtocol

/**
 * Created by uni.x.bell on 10/9/15.
 */
trait ModelMarshalling extends DefaultJsonProtocol {
  import EventSourceManager.Models._
  import EventSource.Models._

  implicit val createEventSourceReqeustFormat = jsonFormat2(CreateEventSourceRequest)
  implicit val createEventSourceResponseFormat = jsonFormat1(CreateEventSourceResponse)
  implicit val eventSourceResponseFormat = jsonFormat3(EventSourceResponse)
  implicit val getEventSourcesRequestFormat = jsonFormat1(GetEventSourcesResponse)
  implicit val deleteEventSourceRequest = jsonFormat1(DeleteEventSourceRequest)
  implicit val deleteEVentSourceResponse = jsonFormat2(DeleteEventSourceResponse)
  implicit val event = jsonFormat3(Event)
  implicit val eventFetchRequest = jsonFormat2(EventFetchRequest)
  implicit val eventFetchResponse = jsonFormat1(EventFetchResponse)
  implicit val eventExpungeRequest = jsonFormat2(EventExpungeRequest)
  implicit val eventExpungeResponse = jsonFormat1(EventExpungeResponse)
}
