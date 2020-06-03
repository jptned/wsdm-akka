package microservice

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import microservice.Webserver.Message
import play.api.libs.json.Json

trait Stockservice {
  implicit val ct: ActorContext[Message]

  val stockRoutes: Route =
    pathPrefix("stock") {
      concat(
        path("find" / LongNumber) { itemId =>
          get {
            val stock = 42
            val price = 100
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("stock" -> stock, "price" -> price).toString()))
          }
        },
        path("subtract" / LongNumber / LongNumber) { (itemId, number) =>
          post {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("add" / LongNumber / LongNumber) { (itemId, number) =>
          post {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("item" / "create" / LongNumber) { price =>
          post {
            val itemId = 1
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("item_id" -> itemId).toString()))
          }
        },
      )
    }
}
