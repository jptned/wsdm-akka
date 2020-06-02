package microservice

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, get, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import play.api.libs.json.{JsValue, Json}


trait Paymentservice {
  implicit val system: ActorSystem

  val paymentRoutes: Route =
    pathPrefix("payment") {
      concat(
        path("pay" / LongNumber / LongNumber) { (userId, orderId) =>
          post {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("cancel" / LongNumber / LongNumber) { (userId, orderId) =>
          post {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("status" / LongNumber) { orderId =>
          get {
            val paid = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("paid" -> paid).toString()))
          }
        },
      )
    }
  }
