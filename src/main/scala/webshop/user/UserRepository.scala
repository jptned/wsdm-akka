package webshop.user

import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}
import scala.concurrent.duration._



class UserRepository extends PersistentActor with AtLeastOnceDelivery with ActorLogging {

  import UserRepository._

  override def persistenceId = s"UserRepository-${self.path.name}"

  var userClients = Map.empty[UserIdentifier, ActorRef]
  var users = Map.empty[UserIdentifier, User]

  context.setReceiveTimeout(1.hour)

  override def receiveCommand: Receive = {
    case CreateUser =>
      log.info("Create a new user.")
      val user = User(UserIdentifier.generate, 0)
      sender() ! GetUserIdentifierResponse(Some(user.userId))
      persist(UserCreated(user))

    case FindUser(userId) if users.contains(userId) =>
      log.info("Look for user in users.")
      sender() ! GetUserResponse(users.get(userId))

    case RemoveUser(userId) if users.contains(userId) =>
      log.info("Remove user from users.")
      sender() ! Succeed
      persist(RemoveUser(userId))

    case RemoveUser(userId) if !users.contains(userId) =>
      sender() ! Failed("user not in de list")

    case SubtractCredit(userId, amount) if !users.contains(userId) =>
      sender() ! Failed("user not in de list")

    case SubtractCredit(userId, amount) if users(userId).credit - amount < 0 =>
      sender() ! Failed("User has not enough credit")

    case SubtractCredit(userId, amount) if users(userId).credit - amount >= 0 =>
      sender() ! Succeed
      persist(CreditSubtracted(userId, amount))

    case AddCredit(userId, amount) if !users.contains(userId) =>
      sender() ! Failed("user not in de list")

    case AddCredit(userId, amount) if users.contains(userId) =>
      sender() ! Succeed
      persist(CreditAdded(userId, amount))

//    case CheckCreditAmount(userId, amount) if !users.contains(userId) =>
//
//    case CheckCreditAmount(userId, amount) if users(userId).credit - amount < 0 =>
//
//    case CheckCreditAmount(userId, amount) if users(userId).credit - amount >= 0 =>


  }

  override def receiveRecover: Receive = {
    case event: UserEvent => handleEvent(event)
  }

  private def handleEvent(event: UserEvent): Unit = event match {
    case UserCreated(user) =>
      users += user.userId -> user
      if (recoveryFinished) {
        userClients += user.userId -> sender()
      }
    case UserRemoved(userId) =>
      users.-(userId)

    case CreditSubtracted(userId, amount) =>
      users(userId).credit -= amount

    case CreditAdded(userId, amount) =>
      users(userId).credit += amount
  }

}


object UserRepository {

  def props(): Props = Props(new UserRepository)

  final case class User(userId: UserIdentifier, var credit: Long)

  case class UserIdentifier(userId: UUID) extends AnyVal
  object UserIdentifier {
    def generate = UserIdentifier(UUID.randomUUID())
  }

  sealed trait UserCommand
  final case object CreateUser extends UserCommand
  final case class FindUser(id: UserIdentifier) extends UserCommand
  final case class RemoveUser(id: UserIdentifier) extends UserCommand
  final case class SubtractCredit(id: UserIdentifier, amount: Long) extends UserCommand
  final case class AddCredit(id: UserIdentifier, amount: Long) extends UserCommand
  final case class CheckCreditAmount(id: UserIdentifier, amount: Long) extends UserCommand

  sealed trait UserEvent
  case class UserCreated(user: User) extends UserEvent
  case class UserRemoved(userId: UserIdentifier) extends UserEvent
  case class CreditAdded(userId: UserIdentifier, amount: Long) extends UserEvent
  case class CreditSubtracted(userId: UserIdentifier, amount: Long) extends UserEvent

  sealed trait UserResponse
  case class GetUserIdentifierResponse(userId: Option[UserIdentifier]) extends UserResponse
  case class GetUserResponse(maybeUser: Option[User]) extends UserResponse
  case object Succeed extends UserResponse
  final case class Failed(reason: String) extends UserResponse

}
