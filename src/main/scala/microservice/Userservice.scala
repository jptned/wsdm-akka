package microservice

import java.util.UUID

import actors.UserActor
import actors.UserActor.UserResponse
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post}
import akka.http.scaladsl.server.Route
import microservice.Webserver.Message
import play.api.libs.json.Json
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.duration._


class Userservice(implicit system: ActorSystem[_], implicit val ct: ActorContext[Message]) {
  implicit val timeout: Timeout = Timeout(5000.millis)
//  implicit val scheduler: Scheduler = system.scheduler


  val userRoutes: Route =
    pathPrefix("users") {
      concat(
        path("create") {
          get {
            val id = UUID.randomUUID().toString
            val actor = ct.spawn(UserActor(id), "user-"+id)
            
            val res = actor.ask(UserActor.CreateUser)

            rejectEmptyResponse {
              onSuccess(res) {
                case UserActor.Successful() =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("user_id" -> id).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.InternalServerError)
              }
            }
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
