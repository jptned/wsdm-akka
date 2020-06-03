package microservice

import java.util.UUID

import actors.User
import actors.User.{CreateUser, Successful}
import akka.actor.typed.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post}
import akka.http.scaladsl.server.Route
import microservice.Webserver.Message
import play.api.libs.json.Json

trait Userservice {
  implicit val ct: ActorContext[Message]
  implicit val self: ActorRef[]

  val userRoutes: Route =
    pathPrefix("users") {
      concat(
        path("create") {
          post {
            val actor = ct.spawn(User(UUID.randomUUID().toString), "user-create")
            ct.ask(actor, User.CreateUser) {
              case UserResponse() => AdaptedResponse(message)
              case Failure(_) => AdaptedResponse("Request failed")
            }
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
