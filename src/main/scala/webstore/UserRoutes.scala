package webstore

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern.Askable


class UserRoutes(userStorage: ActorRef[UserStorage.Command[_]])(implicit val system: ActorSystem[_]) extends JsonFormats  {

  import UserStorage._


  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler

  val userRoutes: Route = {
    pathPrefix("users") {
      concat(
        path("create") {
          post {
            val identifierResponse: Future[CreateUserResult] = userStorage.ask(CreateUser)
            rejectEmptyResponse {
              onSuccess(identifierResponse) {
                case Created(userId) => complete(userId)
              }
            }
          }
        },
        path("find" / Segment) { userId =>
          get {
            val maybeUser: Future[FindUserResult] = userStorage.ask(FindUser(UserId(userId), _))
            rejectEmptyResponse {
              onSuccess(maybeUser) {
                case UserFound(user) => complete(user)
                case UserNotFound(_) => complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("remove" / Segment) { userId =>
          delete {
            val operationPerformed: Future[RemoveUserResult] = userStorage.ask(RemoveUser(UserId(userId), _))
            onSuccess(operationPerformed) {
              case RemovedSucceed => complete(StatusCodes.OK)
              case RemovedFailed => complete(StatusCodes.InternalServerError)
            }
          }
        },
        path("credit" / "subtract" / Segment / LongNumber) { (userId, amount) =>
          post {
            val operationPerformed: Future[ChangeCreditResult] = userStorage.ask(SubtractCredit(UserId(userId), amount, _))
            onSuccess(operationPerformed) {
              case ChangedFailed => complete(StatusCodes.InternalServerError)
              case ChangedSucceed => complete(StatusCodes.OK)
            }
          }
        },
        path("credit" / "add" / Segment / LongNumber) { (userId, amount) =>
          post {
            val operationPerformed: Future[ChangeCreditResult] = userStorage.ask(AddCredit(UserId(userId), amount, _))
            onSuccess(operationPerformed) {
              case ChangedFailed => complete(StatusCodes.InternalServerError)
              case ChangedSucceed => complete(StatusCodes.OK)
            }
          }
        })
    }
  }
}