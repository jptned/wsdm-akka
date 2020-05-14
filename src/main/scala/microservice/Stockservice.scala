package microservice

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route

trait Stockservice {
  implicit val system:ActorSystem

  val stockRoutes: Route =
    concat(
      pathPrefix("stock" / "find" / LongNumber) { itemId =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"stock\":\"42\",\"price\":\"500\"}"))
        }
      },
      pathPrefix("stock" / "subtract" / LongNumber / LongNumber) { (itemId, number) =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("stock" / "add" / LongNumber / LongNumber) { (itemId, number) =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      path("stock" / "item" / "create" / LongNumber) { price =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"item_id\":\"1\"}"))
        }
      },
    )
}
