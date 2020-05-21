package microservice

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post}
import akka.http.scaladsl.server.Route
import play.api.libs.json.Json

trait Userservice {
  implicit val system:ActorSystem

  val userRoutes: Route =
    pathPrefix("users") {
      concat(
        path("create") {
          post {
            val userId = 1
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("user_id" -> 1).toString()))
          }
        },
        pathPrefix("remove" / LongNumber) { id =>
          delete {
            val success = true
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        pathPrefix("get" / LongNumber) { id =>
          get {
            val userId = 42
            val credit = 100
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("user_id" -> userId, "credit" -> credit).toString()))
          }
        },
        pathPrefix("credit" / "subtract" / LongNumber / LongNumber) { (id, amount) =>
          post {
            val userId = 42
            val credit = 100 - amount
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("user_id" -> userId, "credit" -> credit).toString()))
          }
        },
        pathPrefix("credit" / "add" / LongNumber / LongNumber) { (id, amount) =>
          post {
            val userId = 42
            val credit = 100 + amount
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("user_id" -> userId, "credit" -> credit).toString()))
          }
        },
      )
    }
}
