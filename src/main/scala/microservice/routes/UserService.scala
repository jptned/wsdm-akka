package microservice.routes

import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.ddata.SelfUniqueAddress
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, concat, delete, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import microservice.actors.UserActor
import microservice.actors.UserActor.{User, UserResponse}
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._


class UserService(implicit val system: ActorSystem[_], implicit val ct: ActorContext[Nothing], implicit val node: SelfUniqueAddress) {
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
                  complete(StatusCodes.BadRequest)
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
                  complete(StatusCodes.OK)
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
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
                  complete(StatusCodes.BadRequest)
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
                  complete(StatusCodes.OK)
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
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
                  complete(StatusCodes.OK)
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
              }
            }
          }
        },
      )
    }
}
