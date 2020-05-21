package webshop.user

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps

import scala.collection.immutable


object UserRepository {

  final case class User(userId: UserIdentifier, var credit: Long)
  final case class Users(users: immutable.Seq[User])

  case class UserIdentifier(userId: UUID) extends AnyVal
  object UserIdentifier {
    def generate = UserIdentifier(UUID.randomUUID())
  }

  sealed trait Command
  final case class CreateUser(replyTo: ActorRef[GetUserIdentifierResponse]) extends Command
  final case class FindUser(id: UserIdentifier, replyTo: ActorRef[GetUserResponse]) extends Command
  final case class RemoveUser(id: UserIdentifier, replyTo: ActorRef[Response]) extends Command
  final case class CreditSubtract(id: UserIdentifier, amount: Long, replyTo: ActorRef[CreditStatus]) extends Command
  final case class CreditAdd(id: UserIdentifier, amount: Long, replyTo: ActorRef[CreditSuccess]) extends Command
  final case class CheckCreditAmount(id: UserIdentifier, amount: Long) extends Command

  final case class GetUserIdentifierResponse(identifier: Option[UserIdentifier])
  final case class GetUserResponse(maybeUser: Option[User])
  final case class ActionPerformed(description: String)

  sealed trait CreditStatus
  final case class CreditFailure(description: String) extends CreditStatus
  final case class CreditSuccess() extends CreditStatus

  // Trait defining successful and failure responses
  sealed trait Response
  case object OK extends Response
  final case class KO(reason: String) extends Response

  def apply(): Behavior[Command] = registry(Map.empty)

  private def registry(users: Map[UserIdentifier, User]): Behavior[Command] = Behaviors.receiveMessage {
    case CreateUser(replyTo) =>
      val user = User(UserIdentifier.generate, 0)
      replyTo ! GetUserIdentifierResponse(Some(user.userId))
      registry(users.+(user.userId -> user))
    case FindUser(id, replyTo) =>
      replyTo ! GetUserResponse(users.get(id))
      Behaviors.same
    case RemoveUser(id, replyTo) if users.contains(id) =>
      replyTo ! OK
      registry(users.-(id))
    case RemoveUser(id, replyTo) =>
      replyTo ! KO("user not in de list")
      Behaviors.same
    case CreditSubtract(id, amount, replyTo) if users(id).credit - amount < 0 =>
      replyTo ! CreditFailure("Credit is too small to buy the product.")
      Behaviors.same
    case CreditSubtract(id, amount, replyTo) =>
      replyTo ! CreditSuccess()
      users(id).credit -= amount
      registry(users)
//      Behaviors.same
    case CreditAdd(id, amount, replyTo) =>
      replyTo ! CreditSuccess()
      users(id).credit += amount
      registry(users)
//    case CheckCreditAmount(id, amount) if users(id).credit - amount < 0 =>
//      sender() ! PaymentService.Failed
//    case CheckCreditAmount(id, amount)  =>
//      sender() ! PaymentService.Succeed

  }

}
