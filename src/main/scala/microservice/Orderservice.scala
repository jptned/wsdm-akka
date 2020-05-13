package microservice

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route

trait Orderservice {
  implicit val system:ActorSystem

  val orderRoutes: Route =
    concat(
      path("orders" / "create") {
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"id\":\"1\"}"))
        }
      },
      pathPrefix("orders" / "remove" / LongNumber) { orderId =>
        delete {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("orders" / "find" / LongNumber) { orderId =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"order_id\":\"" + orderId + "\",\"paid\":\"true\",\"items\":{\"4\",\"2\"},\"user\":\"6\",\"total_cost\":\"100\"}"))
        }
      },
      pathPrefix("orders" / "addItem" / LongNumber / LongNumber) { (orderId, itemId) =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("orders" / "deleteItem" / LongNumber / LongNumber) { (orderId, itemId) =>
        delete {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("orders" / "checkout" / LongNumber) { (orderId) =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
    )
}
