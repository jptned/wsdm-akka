package microservice

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.io.StdIn

object WebServer {
  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route = concat(
      path("users" / "create") {
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"id\":\"1\"}"))
        }
      },
      pathPrefix("users" / "delete" / LongNumber) { id =>
        delete {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("users" / "delete" / LongNumber) { id =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("users" / "get" / LongNumber) { id =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"userId\":\""+ id +"\",\"credit\":\"10\"}"))
        }
      },
      pathPrefix("users" / "credit" / "subtract" / LongNumber / LongNumber) { (id, amount) =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"userId\":\""+ id +"\",\"credit\":\""+ (100 - amount) +"\"}"))
        }
      },
      pathPrefix("users" / "credit" / "add" / LongNumber / LongNumber) { (id, amount) =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"userId\":\""+ id +"\",\"credit\":\""+ (100 + amount) +"\"}"))
        }
      },
    )
    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}