package microservice

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import microservice.Webserver.Message
import play.api.libs.json.{JsValue, Json}

trait Orderservice {
  implicit val ct: ActorContext[Message]

  val orderRoutes: Route =
    pathPrefix("orders") {
      concat(
        path("create" / LongNumber) { userId =>
          post {
            val orderId = 1
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("order_id" -> orderId).toString()))
          }
        },
        path("remove" / LongNumber) { orderId =>
          delete {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("find" / LongNumber) { orderId =>
          get {
            val paid = true
            val user = 5
            val totalCost = 100
            val json: JsValue = Json.obj(
              "order_id" -> orderId,
              "paid" -> paid,
              "items" -> Json.arr(),
              "user" -> user,
              "total_cost" -> totalCost
            )
            complete(HttpEntity(ContentTypes.`application/json`, json.toString()))
          }
        },
        path("addItem" / LongNumber / LongNumber) { (orderId, itemId) =>
          post {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("removeItem" / LongNumber / LongNumber) { (orderId, itemId) =>
          delete {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("checkout" / LongNumber) { (orderId) =>
          post {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
      )
    }
}
