package io.github.xbay.yak

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import spray.can.Http

/**
 * Created by uni.x.bell on 10/8/15.
 */
object Main extends App
  with RequestTimeout
  with ShutdownIfNotBound {
  val config = ConfigFactory.load()
  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  implicit val system = ActorSystem("yak")
  implicit val executionContext = system.dispatcher
  implicit val timeout = requestTimeout(config)

  val api = system.actorOf(Props(new RestApi(timeout)), "httpInterface")

  val response = IO(Http).ask(Http.Bind(listener = api, interface = host, port = port))
  shutdownIfNotBound(response)
}

trait RequestTimeout {
  import scala.concurrent.duration._
  def requestTimeout(config: Config): Timeout = { //<co id="ch02_timeout_spray_can"/>
  val t = config.getString("spray.can.server.request-timeout")
    val d = Duration(t)
    FiniteDuration(d.length, d.unit)
  }
}

trait ShutdownIfNotBound {
  import scala.concurrent.ExecutionContext
  import scala.concurrent.Future

  val logger = LoggerFactory.getLogger(this.getClass)
  def shutdownIfNotBound(f: Future[Any]) //<co id="ch02_shutdownIfNotBound"/>
                        (implicit system: ActorSystem, ec: ExecutionContext) = {
    f.mapTo[Http.Event].map {
      case Http.Bound(address) =>
        logger.info(s"REST interface bound to $address")
      case Http.CommandFailed(cmd) => //<co id="http_command_failed"/>
        logger.info(s"REST interface could not bind: ${cmd.failureMessage}, shutting down.")
        system.shutdown()
    }.recover {
      case e: Throwable =>
        logger.info(s"Unexpected error binding to HTTP: ${e.getMessage}, shutting down.")
        system.shutdown()
    }
  }
}
