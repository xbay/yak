package io.github.xbay.controller

import com.twitter.finagle.httpx.Request
import com.twitter.finatra.http.Controller

/**
 * Created by alan7yg on 15/9/14.
 */
class HelloFinatraController extends Controller {
  get("/hi") { request: Request =>
    val name = request.params.getOrElse("name", "Finatra")
    s"Hellooooooooooooooooo, $name"
  }

}
