package microservice.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.AskPattern.Askable
import microservice.models.{UserManager, UserStorage}
import microservice.models.UserStorage.UserResponse
import microservice.setups.Initials.UserId
import microservice.setups.JsonFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class UserRoutes(userManager: ActorRef[UserManager.UserManagerCommand])(implicit val system: ActorSystem[_]) extends JsonFormats {

  private implicit lazy val internalTimeout = Timeout(5000.milliseconds)
  implicit val scheduler = system.scheduler

  lazy val userRoutes: Route =
    pathPrefix("users") {
      concat(
        path("create") {
          get {
            val identifierResponse: Future[UserResponse] = userManager.ask(UserManager.CreateUser)
            rejectEmptyResponse {
              onSuccess(identifierResponse) {
                case UserStorage.Created(id) => complete(id)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("find" / Segment) { userId =>
          get {
            val maybeUser: Future[UserResponse] = userManager.ask(UserManager.FindUser(UserId(userId), _))
            rejectEmptyResponse {
              onSuccess(maybeUser) {
                case UserStorage.UserFound(user) => complete(user)
                case UserStorage.UserNotFound(id) => complete(StatusCodes.InternalServerError -> id.id)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("remove" / Segment) { userId =>
          delete {
            val operationPerformed: Future[UserResponse] = userManager.ask(UserManager.RemoveUser(UserId(userId), _))
            onSuccess(operationPerformed) {
              case UserStorage.RemovedSucceed => complete(StatusCodes.OK)
              case UserStorage.RemovedFailed => complete(StatusCodes.InternalServerError)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        },
        path("credit" / "subtract" / Segment / LongNumber) { (userId, amount) =>
          post {
            val operationPerformed: Future[UserResponse] = userManager.ask(UserManager.SubtractCredit(UserId(userId),
              amount, _))
            onSuccess(operationPerformed) {
              case UserStorage.ChangedCreditSucceed => complete(StatusCodes.OK)
              case UserStorage.ChangedCreditFailed => complete(StatusCodes.InternalServerError)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        },
        path("credit" / "add" / Segment / LongNumber) { (userId, amount) =>
          delete {
            val operationPerformed: Future[UserResponse] = userManager.ask(UserManager.AddCredit(UserId(userId),
              amount, _))
            onSuccess(operationPerformed) {
              case UserStorage.ChangedCreditSucceed => complete(StatusCodes.OK)
              case UserStorage.ChangedCreditFailed => complete(StatusCodes.InternalServerError)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      )
    }

}
