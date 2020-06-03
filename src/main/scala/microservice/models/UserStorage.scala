package microservice.models

import akka.persistence.typed.PersistenceId
import microservice.setups.Initials.{User, UserId, UserStorageEntityId}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}


object UserStorage {

  sealed trait UserCommand {
    def userId: UserId
  }
  sealed trait UserEvent
  sealed trait UserResponse

  final case object UserGracefulStop extends UserCommand {
    // this message is intended to be sent directly from the parent shard, hence the orderId is irrelevant
    override def userId: UserId = UserId("")
  }

  // the protocol for creating an user
  final case class CreateUser(userId: UserId, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class UserCreated(userId: UserId) extends UserEvent
  case class Created(userId: UserId) extends UserResponse

  // the protocol for looking up an user by its id
  final case class FindUser(userId: UserId, replyTo: ActorRef[UserResponse]) extends UserCommand
  case class UserFound(user: User) extends UserResponse
  case class UserNotFound(id: UserId) extends UserResponse

  // the protocol for removing an user by its id
  final case class RemoveUser(userId: UserId, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class UserRemoved(userId: UserId) extends UserEvent
  case object RemovedSucceed extends UserResponse
  case object RemovedFailed extends UserResponse

  // the protocol for adding and subtracting from the credit of an user by its id
  final case class SubtractCredit(userId: UserId, amount: Long, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class CreditSubtracted(userId: UserId, amount: Long) extends UserEvent
  final case class AddCredit(userId: UserId, amount: Long, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class CreditAdded(userId: UserId, amount: Long) extends UserEvent
  case object ChangedCreditSucceed extends UserResponse
  case object ChangedCreditFailed extends UserResponse

  // state definition
  final case class Storage(users: Map[UserId, User] = Map.empty) {
    def applyEvent(event: UserEvent): Storage = event match {
      case UserCreated(userId) =>
        copy(users = users.updated(userId, User(userId, 0)))
      case UserRemoved(userId) =>
        copy(users = users.-(userId))
      case CreditSubtracted(userId, amount) =>
        users(userId).credit -= amount
        copy(users = users.updated(userId, users(userId)))
      case CreditAdded(userId, amount) =>
        users(userId).credit += amount
        copy(users = users.updated(userId, users(userId)))

    }
    def applyCommand(context: ActorContext[UserCommand], cmd: UserCommand): ReplyEffect[UserEvent, Storage] = cmd match {
      case CreateUser(userId, replyTo) =>
        print("userId " + userId)
        val event = UserCreated(userId)
        Effect.persist(event).thenReply(replyTo)(_ => Created(event.userId))
      case FindUser(id, replyTo) if users.contains(id) =>
        Effect.reply(replyTo)(UserFound(users(id)))
      case FindUser(id, replyTo) if !users.contains(id) =>
        Effect.reply(replyTo)(UserNotFound(id))
      case RemoveUser(id, replyTo) if users.contains(id) =>
        val event = UserRemoved(id)
        Effect.persist(event).thenReply(replyTo)(_ => RemovedSucceed)
      case RemoveUser(id, replyTo) if !users.contains(id) =>
        Effect.reply(replyTo)(RemovedFailed)
      case SubtractCredit(id, amount, replyTo) if users.contains(id) && users(id).credit - amount < 0 =>
        Effect.reply(replyTo)(ChangedCreditFailed)
      case SubtractCredit(id, amount, replyTo) if users.contains(id) && users(id).credit - amount >= 0 =>
        val event = CreditSubtracted(id, amount)
        Effect.persist(event).thenReply(replyTo)(_ => ChangedCreditSucceed)
      case SubtractCredit(id, _, replyTo) if !users.contains(id) =>
        Effect.reply(replyTo)(ChangedCreditFailed)
      case AddCredit(id, amount, replyTo) if users.contains(id) =>
        val event = CreditAdded(id, amount)
        Effect.persist(event).thenReply(replyTo)(_ => ChangedCreditSucceed)
      case AddCredit(id, _, replyTo) if !users.contains(id) =>
        Effect.reply(replyTo)(ChangedCreditFailed)
    }
  }

  def apply(userStorageEntityId: UserStorageEntityId, persistenceId: PersistenceId): Behavior[UserCommand] = Behaviors.setup { context =>
    EventSourcedBehavior.withEnforcedReplies[UserCommand, UserEvent, Storage](
      persistenceId = persistenceId,
      emptyState = Storage(),
      commandHandler = (state, cmd) => state.applyCommand(context, cmd),
      eventHandler = (state, evt) => state.applyEvent(evt))
  }

}
