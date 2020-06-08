package microservice

import java.util.UUID

import actors.UserActor
import actors.UserActor.{User, UserResponse}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post}
import akka.http.scaladsl.server.Route
import microservice.Webserver.Message
import play.api.libs.json.Json
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._


class Userservice(implicit system: ActorSystem[_], implicit val ct: ActorContext[Message]) {
  implicit val timeout: Timeout = Timeout(5000.millis)

  val userRoutes: Route =
    pathPrefix("users") {
      concat(
        path("create") {
          post {
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
        pathPrefix("remove" / JavaUUID) { id =>
          delete {
            val actor = ct.spawn(UserActor(id.toString), "user-"+id)
            val res = actor.ask(UserActor.DeleteUser)
            rejectEmptyResponse {
              onSuccess(res) {
                case UserActor.Successful() =>
                  ct.stop(actor)
                  val success = true
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
                case _ =>
                  ct.stop(actor)
                  val success = false
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
              }
            }
          }
        },
        pathPrefix("get" / JavaUUID) { id =>
          get {
            val actor = ct.spawn(UserActor(id.toString), "user-"+id)
            val res = actor.ask(UserActor.FindUser)
            rejectEmptyResponse {
              onSuccess(res) {
                case User(user_id, creditValue) =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("user_id" -> user_id, "credit" -> creditValue).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        pathPrefix("credit" / "subtract" / JavaUUID / LongNumber) { (id, amount) =>
          post {
            val actor = ct.spawn(UserActor(id.toString), "user-"+id)
            val res: Future[UserResponse] = actor.ask(UserActor.SubtractCredit(amount, _))
            rejectEmptyResponse {
              onSuccess(res) {
                case UserActor.Successful() =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> true).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> false).toString()))
              }
            }
          }
        },
        pathPrefix("credit" / "add" / JavaUUID / LongNumber) { (id, amount) =>
          post {
            val actor = ct.spawn(UserActor(id.toString), "user-"+id)
            val res: Future[UserResponse] = actor.ask(UserActor.AddCredit(amount, _))
            rejectEmptyResponse {
              onSuccess(res) {
                case UserActor.Successful() =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> true).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> false).toString()))
              }
            }
          }
        },
      )
    }
}
