package webshop.user

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.ActorContext
import webshop.JsonFormats
import akka.util.Timeout
import java.util.UUID

import scala.concurrent.duration._

import scala.concurrent.Future

class UserRoutes(userRepository: ActorRef[UserRepository.Command])(implicit val system: ActorSystem[_]) extends JsonFormats  {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import UserRepository._
  import akka.actor.typed.scaladsl.AskPattern.Askable

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler


   val userRoutes: Route = {
     pathPrefix("users") {
       concat(
         path("create") {
           post {
             val identifierResponse: Future[GetUserIdentifierResponse] = userRepository.ask(UserRepository.CreateUser)
             rejectEmptyResponse {
               onSuccess(identifierResponse) { response =>
                 complete(response.identifier)
               }
             }
           }
         },
         path("find" / Segment) { userId =>
           get {
              val maybeUser: Future[GetUserResponse] = userRepository.ask(UserRepository.FindUser(UserIdentifier(UUID
                .fromString(userId)), _))
              rejectEmptyResponse {
                onSuccess(maybeUser) { response =>
                  complete(response.maybeUser)
                }
              }
           }
         },
         path("remove" / Segment) { userId =>
           delete {
             val operationPerformed: Future[Response] = userRepository.ask(UserRepository.RemoveUser(UserIdentifier(UUID.fromString(userId)), _))
//             onSuccess(UserRegistry.DeleteUser(id)) { performed =>
//               complete((StatusCodes.OK), performed)
//             }
             onSuccess(operationPerformed) {
               case UserRepository.OK => complete(StatusCodes.OK)
               case UserRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
             }
           }
         },
         path("credit" / "subtract" / Segment / LongNumber) { (userId, amount) =>
           post {
             val operationPerformed: Future[CreditStatus] = userRepository.ask(UserRepository.CreditSubtract(UserIdentifier(UUID.fromString(userId)), amount, _))
             onSuccess(operationPerformed) {
               case UserRepository.CreditFailure(reason) => complete(StatusCodes.InternalServerError -> reason)
               case UserRepository.CreditSuccess() => complete(StatusCodes.OK)
             }
           }
         },
         path("credit" / "add" / Segment / LongNumber) { (userId, amount) =>
           post {
             val operationPerformed: Future[CreditStatus] = userRepository.ask(UserRepository.CreditAdd(UserIdentifier(UUID.fromString(userId)), amount, _))
             onSuccess(operationPerformed) { _=>
               complete(StatusCodes.OK)
             }
           }
         })
     }
   }
}
