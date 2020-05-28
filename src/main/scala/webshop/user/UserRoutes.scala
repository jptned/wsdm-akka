package webshop.user

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webshop.JsonFormats
import akka.util.Timeout
import java.util.UUID

import scala.concurrent.duration._

import scala.concurrent.Future
import akka.pattern.ask

class UserRoutes(userRepository: ActorRef)(implicit val system: ActorSystem) extends JsonFormats  {

  import UserRepository._

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler


   val userRoutes: Route = {
     pathPrefix("users") {
       concat(
         path("create") {
           post {
             val identifierResponse: Future[GetUserIdentifierResponse] = (userRepository ? UserRepository.CreateUser)
               .mapTo[GetUserIdentifierResponse]
             rejectEmptyResponse {
               onSuccess(identifierResponse) { response =>
                 complete(response.userId)
               }
             }
           }
         },
         path("find" / Segment) { userId =>
           get {
              val maybeUser: Future[GetUserResponse] = (userRepository ? UserRepository.FindUser(UserIdentifier(UUID.fromString(userId)))).mapTo[GetUserResponse]
              rejectEmptyResponse {
                onSuccess(maybeUser) { response =>
                  complete(response.maybeUser)
                }
              }
           }
         },
         path("remove" / Segment) { userId =>
           delete {
             val operationPerformed: Future[UserResponse] = (userRepository ? UserRepository.RemoveUser(UserIdentifier(UUID.fromString(userId)))).mapTo[UserResponse]
             onSuccess(operationPerformed) {
               case UserRepository.Succeed => complete(StatusCodes.OK)
               case UserRepository.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
             }
           }
         },
         path("credit" / "subtract" / Segment / LongNumber) { (userId, amount) =>
           post {
             val operationPerformed: Future[UserResponse] = (userRepository ? UserRepository.SubtractCredit(UserIdentifier(UUID.fromString(userId)), amount)).mapTo[UserResponse]
             onSuccess(operationPerformed) {
               case UserRepository.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
               case UserRepository.Succeed => complete(StatusCodes.OK)
             }
           }
         },
         path("credit" / "add" / Segment / LongNumber) { (userId, amount) =>
           post {
             val operationPerformed: Future[UserResponse] = (userRepository ? UserRepository.AddCredit(UserIdentifier(UUID.fromString(userId)), amount)).mapTo[UserResponse]
             onSuccess(operationPerformed) {
               case UserRepository.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
               case UserRepository.Succeed => complete(StatusCodes.OK)
             }
           }
         })
     }
   }
}
