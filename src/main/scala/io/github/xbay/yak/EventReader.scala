package io.github.xbay.yak

import akka.actor._
import akka.util.Timeout

/**
 * Created by uni.x.bell on 10/14/15.
 */

abstract class EventReader{
  def pause()

  def resume()
}
