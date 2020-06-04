package webshop.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.AskPattern.Askable
import webshop.JsonFormats
import webshop.actors.UserActor
import webshop.actors.UserActor.UserResponse


import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class UserRoutes(userActor: ActorRef[UserActor.Command])(implicit val system: ActorSystem[_]) extends JsonFormats {

  private implicit lazy val internalTimeout = Timeout(5000.milliseconds)
  implicit val scheduler = system.scheduler

  lazy val userRoutes: Route =
    pathPrefix("users") {
      concat(
        path("create") {
          get {
            val identifierResponse: Future[UserResponse] = userActor.ask(UserActor.CreateUser)
            rejectEmptyResponse {
              onSuccess(identifierResponse) {
                case UserActor.Created(id) => complete(id)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("find" / Segment) { userId =>
          get {
            val maybeUser: Future[UserResponse] = userActor.ask(UserActor.FindUser(userId, _))
            rejectEmptyResponse {
              onSuccess(maybeUser) {
                case UserActor.UserFound(user) => complete(user)
                case UserActor.UserNotFound(reason) => complete(StatusCodes.InternalServerError -> reason)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("remove" / Segment) { userId =>
          delete {
            val operationPerformed: Future[UserResponse] = userActor.ask(UserActor.DeleteUser(userId, _))
            onSuccess(operationPerformed) {
              case UserActor.RemovedSucceed => complete(StatusCodes.OK)
              case UserActor.RemovedFailed(reason) => complete(StatusCodes.InternalServerError -> reason)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        },
        path("credit" / "subtract" / Segment / LongNumber) { (userId, amount) =>
          get {
            val operationPerformed: Future[UserResponse] = userActor.ask(UserActor.SubtractCredit(userId,
              amount, _))
            onSuccess(operationPerformed) {
              case UserActor.ChangedCreditSucceed => complete(StatusCodes.OK)
              case UserActor.ChangedCreditFailed => complete(StatusCodes.InternalServerError)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        },
        path("credit" / "add" / Segment / LongNumber) { (userId, amount) =>
          get {
            val operationPerformed: Future[UserResponse] = userActor.ask(UserActor.AddCredit(userId,
              amount, _))
            onSuccess(operationPerformed) {
              case UserActor.ChangedCreditSucceed => complete(StatusCodes.OK)
              case UserActor.ChangedCreditFailed => complete(StatusCodes.InternalServerError)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      )
    }

}
