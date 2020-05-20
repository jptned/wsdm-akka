package webshop.order

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import webshop.JsonFormats
import webshop.user.UserRepository
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webshop.user.UserRepository._

import scala.concurrent.Future
import scala.concurrent.duration._


class OrderRoutes(orderRepository: ActorRef[UserRepository.Command])(implicit val system: ActorSystem[_]) extends JsonFormats {

  import OrderHandler._
  import akka.actor.typed.scaladsl.AskPattern.Askable

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler

  val orderRoutes: Route = {
    pathPrefix("orders") {
      concat(
        path("create" / Segment) { userId =>
          post {
            val identifierResponse: Future[GetUserIdentifierResponse] = orderRepository.ask(UserRepository.CreateUser)
            rejectEmptyResponse {
              onSuccess(identifierResponse) { response =>
                complete(response.identifier)
              }
            }
          }
        },
        path("find" / Segment) { orderId =>
          get {
            val maybeUser: Future[GetUserResponse] = orderRepository.ask(UserRepository.FindUser(UserIdentifier(UUID
              .fromString(userId)), _))
            rejectEmptyResponse {
              onSuccess(maybeUser) { response =>
                complete(response.maybeUser)
              }
            }
          }
        },
        path("remove" / Segment) { orderId =>
          delete {
            val operationPerformed: Future[Response] = orderRepository.ask(UserRepository.RemoveUser(UserIdentifier(UUID.fromString(userId)), _))
            //             onSuccess(UserRegistry.DeleteUser(id)) { performed =>
            //               complete((StatusCodes.OK), performed)
            //             }
            onSuccess(operationPerformed) {
              case UserRepository.OK => complete(StatusCodes.OK)
              case UserRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("addItem" / Segment / Segment) { (orderId, itemId) =>
          post {
            val operationPerformed: Future[CreditStatus] = userRepository.ask(UserRepository.CreditSubtract(UserIdentifier(UUID.fromString(userId)), amount, _))
            onSuccess(operationPerformed) {
              case UserRepository.CreditFailure(reason) => complete(StatusCodes.InternalServerError -> reason)
              case UserRepository.CreditSuccess() => complete(StatusCodes.OK)
            }
          }
        },
        path("removeItem" / Segment / Segment) { (orderId, itemId) =>
          post {
            val operationPerformed: Future[CreditStatus] = userRepository.ask(UserRepository.CreditSubtract(UserIdentifier(UUID.fromString(userId)), amount, _))
            onSuccess(operationPerformed) {
              case UserRepository.CreditFailure(reason) => complete(StatusCodes.InternalServerError -> reason)
              case UserRepository.CreditSuccess() => complete(StatusCodes.OK)
            }
          }
        },
        path("checkout" / Segment) { orderId =>
          post {
            val identifierResponse: Future[GetUserIdentifierResponse] = orderRepository.ask(UserRepository.CreateUser)
            rejectEmptyResponse {
              onSuccess(identifierResponse) { response =>
                complete(response.identifier)
              }
            }
          }
        }
      )
    }
  }


}
