package microservice

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, get, pathPrefix, post, _}
import akka.http.scaladsl.server.Route

trait Paymentservice {
  implicit val system:ActorSystem

  val paymentRoutes: Route =
    concat(
      pathPrefix("payment" / "pay" / LongNumber / LongNumber) { (userId, orderId) =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("payment" / "cancel" / LongNumber / LongNumber) { (userId, orderId) =>
        post {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"success\":\"true\"}"))
        }
      },
      pathPrefix("payment" / "status" / LongNumber) { orderId =>
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"paid\":\"true\"}"))
        }
      },
    )
}
