package webstore
import java.util.UUID

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect }


object UserStorage {

  case class UserId(id: String) extends AnyVal
  case class StoredUser(id: UserId, var credit: Long)

  sealed trait Command[Reply <: CommandReply] {
    def replyTo: ActorRef[Reply]
  }
  sealed trait Event
  sealed trait CommandReply

  // the protocol for creating an user
  final case class CreateUser(replyTo: ActorRef[CreateUserResult]) extends Command[CreateUserResult]
  final case class UserCreated(userId: UserId) extends Event
  sealed trait CreateUserResult extends CommandReply
  case class Created(userId: UserId) extends CreateUserResult

  // the protocol for looking up an user by its id
  final case class FindUser(id: UserId, replyTo: ActorRef[FindUserResult]) extends Command[FindUserResult]
  sealed trait FindUserResult extends CommandReply
  case class UserFound(user: StoredUser) extends FindUserResult
  case class UserNotFound(id: UserId) extends FindUserResult

  // the protocol for removing an user by its id
  final case class RemoveUser(id: UserId, replyTo: ActorRef[RemoveUserResult]) extends Command[RemoveUserResult]
  final case class UserRemoved(userId: UserId) extends Event
  sealed trait RemoveUserResult extends CommandReply
  case object RemovedSucceed extends RemoveUserResult
  case object RemovedFailed extends RemoveUserResult

  // the protocol for adding and subtracting from the credit of an user by its id
  final case class SubtractCredit(id: UserId, amount: Long, replyTo: ActorRef[ChangeCreditResult]) extends Command[ChangeCreditResult]
  final case class CreditSubtracted(userId: UserId, amount: Long) extends Event
  final case class AddCredit(id: UserId, amount: Long, replyTo: ActorRef[ChangeCreditResult]) extends Command[ChangeCreditResult]
  final case class CreditAdded(userId: UserId, amount: Long) extends Event
  sealed trait ChangeCreditResult extends CommandReply
  case object ChangedSucceed extends ChangeCreditResult
  case object ChangedFailed extends ChangeCreditResult

  // state definition
  final case class Storage(users: Map[UserId, StoredUser] = Map.empty) {
    def applyEvent(event: Event): Storage = event match {
      case UserCreated(userId) =>
        copy(users = users.updated(userId, StoredUser(userId, 0)))
      case UserRemoved(userId) =>
        copy(users = users.-(userId))
      case CreditSubtracted(userId, amount) =>
        users(userId).credit -= amount
        copy(users = users.updated(userId, users(userId)))
      case CreditAdded(userId, amount) =>
        users(userId).credit += amount
        copy(users = users.updated(userId, users(userId)))

    }
    def applyCommand(context: ActorContext[Command[_]], cmd: Command[_]): ReplyEffect[Event, Storage] = cmd match {
      case CreateUser(replyTo) =>
        val event = UserCreated(UserId(UUID.randomUUID().toString))
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
        Effect.reply(replyTo)(ChangedFailed)
      case SubtractCredit(id, amount, replyTo) if users.contains(id) && users(id).credit - amount >= 0 =>
        val event = CreditSubtracted(id, amount)
        Effect.persist(event).thenReply(replyTo)(_ => ChangedSucceed)
      case SubtractCredit(id, _, replyTo) if !users.contains(id) =>
        Effect.reply(replyTo)(ChangedFailed)
      case AddCredit(id, amount, replyTo) if users.contains(id) =>
        val event = CreditAdded(id, amount)
        Effect.persist(event).thenReply(replyTo)(_ => ChangedSucceed)
      case AddCredit(id, _, replyTo) if !users.contains(id) =>
        Effect.reply(replyTo)(ChangedFailed)
    }
  }

  def apply(): Behavior[Command[_]] = Behaviors.setup { context =>
    EventSourcedBehavior.withEnforcedReplies[Command[_], Event, Storage](
      persistenceId = PersistenceId.ofUniqueId("us"),
      emptyState = Storage(),
      commandHandler = (state, cmd) => state.applyCommand(context, cmd),
      eventHandler = (state, evt) => state.applyEvent(evt))
  }
}
