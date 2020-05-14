package microservice

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post}
import akka.http.scaladsl.server.Route

trait Userservice {
  implicit val system:ActorSystem

  val userRoutes: Route =
    concat(
      path("users" / "create") {
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"user_id\":\"1\"}"))
        }
      },
      pathPrefix("users" / "remove" / LongNumber) { id =>
        delete {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("users" / "get" / LongNumber) { id =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"user_id\":\"" + id + "\",\"credit\":\"10\"}"))
        }
      },
      pathPrefix("users" / "credit" / "subtract" / LongNumber / LongNumber) { (id, amount) =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"user_id\":\"" + id + "\",\"credit\":\"" + (100 - amount) + "\"}"))
        }
      },
      pathPrefix("users" / "credit" / "add" / LongNumber / LongNumber) { (id, amount) =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"user_id\":\"" + id + "\",\"credit\":\"" + (100 + amount) + "\"}"))
        }
      },
    )
}
