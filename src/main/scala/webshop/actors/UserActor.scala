package webshop.actors

import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Delete, Get, Update}
import akka.cluster.ddata.{ReplicatedData, SelfUniqueAddress}
import webshop.Initials.{User, UserId}
import webshop.types.{NotEnoughCreditException, UserType, UserTypeKey}

import scala.concurrent.duration._

object UserActor {

  sealed trait Command

  final case class FindUser(userId: String, replyTo: ActorRef[UserResponse]) extends Command
  final case class DeleteUser(userId: String, replyTo: ActorRef[UserResponse]) extends Command
  final case class SubtractCredit(userId: String, number: Long, replyTo: ActorRef[UserResponse]) extends Command
  final case class AddCredit(userId: String, number: Long, replyTo: ActorRef[UserResponse]) extends Command
  final case class CreateUser(replyTo: ActorRef[UserResponse]) extends Command

  sealed trait UserResponse
  final case class Created(userId: UserId) extends UserResponse
  final case class UserFound(user: User) extends UserResponse
  final case class UserNotFound(reason: String) extends UserResponse
  final case object RemovedSucceed extends UserResponse
  final case class RemovedFailed(reason: String) extends UserResponse
  final case object ChangedCreditSucceed extends UserResponse
  final case object ChangedCreditFailed extends UserResponse
  final case class Failed(reason: String) extends UserResponse

  private sealed trait InternalCommand extends Command

  private case class InternalFindResponse(userId: String, replyTo: ActorRef[UserResponse], rsp: GetResponse[UserType]) extends InternalCommand
  private case class InternalUpdateResponse[A <: ReplicatedData](userId: String, replyTo: ActorRef[UserResponse], rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalDeleteResponse[A <: ReplicatedData](userId: String, replyTo: ActorRef[UserResponse], rsp: DeleteResponse[A]) extends InternalCommand
  private case class InternalCreateResponse(userId: String, replyTo: ActorRef[UserResponse], getResponse: GetResponse[UserType]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  val userKey: ServiceKey[Command] = ServiceKey("userActor")

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, UserType] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      // register with the Receptionist which makes this actor discoverable
      context.system.receptionist ! Receptionist.Register(userKey, context.self)

      def dataKey(userId: String) = UserTypeKey("user-" + userId)

      def behavior = Behaviors.receiveMessagePartial(
        receiveFindUser
          .orElse(receiveDeleteUser)
          .orElse(receiveSubtractCredit)
          .orElse(receiveAddCredit)
          .orElse(receiveCreateUser)
          .orElse(receiveOther)
      )

      def receiveDeleteUser: PartialFunction[Command, Behavior[Command]] = {
        case DeleteUser(userId, replyTo) =>
          replicator.askDelete(
            askReplyTo => Delete(dataKey(userId), writeMajority, askReplyTo),
            rsp => InternalDeleteResponse(userId, replyTo, rsp)
          )
          Behaviors.same
        case InternalDeleteResponse(userId, replyTo, DeleteSuccess(_, _)) =>
          replyTo ! RemovedSucceed
          Behaviors.same
        case InternalDeleteResponse(userId, replyTo, e@ReplicationDeleteFailure(_, _)) =>
          replyTo ! RemovedFailed("Failed deleting: " + e)
          Behaviors.same
        case InternalDeleteResponse(userId, replyTo, e) =>
          replyTo ! RemovedFailed("Failed deleting: " + e)
          Behaviors.same
      }

      def receiveFindUser: PartialFunction[Command, Behavior[Command]] = {
        case FindUser(userId, replyTo) =>
          replicator.askGet(
            askReplyTo => Get(dataKey(userId), readMajority, askReplyTo),
            rsp => InternalFindResponse(userId, replyTo, rsp))

          Behaviors.same

        case InternalFindResponse(userId, replyTo, g @ GetSuccess(_, _)) =>
          val user = g.get(dataKey(userId))
          replyTo ! UserFound(User(UserId(user.user_id), user.creditValue.toLong))
          Behaviors.same

        case InternalFindResponse(userId, replyTo, _: NotFound[_]) =>
          replyTo ! UserNotFound("Couldn't find " + userId)
          Behaviors.same

        case InternalFindResponse(userId, replyTo, _: GetDataDeleted[_]) =>
          replyTo ! UserNotFound("Couldn't find " + userId)
          Behaviors.same

        case InternalFindResponse(userId, replyTo, GetFailure(_, _)) =>
          // ReadMajority failure, try again with local read
          replicator.askGet(
            askReplyTo => Get(dataKey(userId), ReadLocal, askReplyTo),
            rsp => InternalFindResponse(userId, replyTo, rsp))

          Behaviors.same
      }

      def receiveSubtractCredit: PartialFunction[Command, Behavior[Command]] = {
        case SubtractCredit(userId, number, replyTo) =>
          val DataKey = UserTypeKey("user-" + userId)
          replicator.askUpdate(
            askReplyTo => Update(DataKey, UserType.create(userId), writeMajority, askReplyTo) {
              user => user.decrement(number)
            },
            rsp => InternalUpdateResponse(userId, replyTo, rsp))

          Behaviors.same
      }

      def receiveAddCredit: PartialFunction[Command, Behavior[Command]] = {
        case AddCredit(userId, number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(dataKey(userId), UserType.create(userId), writeMajority, askReplyTo) {
              user => user.increment(number)
            },
            rsp => InternalUpdateResponse(userId, replyTo, rsp))

          Behaviors.same
      }

      def receiveCreateUser: PartialFunction[Command, Behavior[Command]] = {
        case CreateUser(replyTo) =>
          val userId: String = UUID.randomUUID().toString
          print("UserId " + userId)
          replicator.askUpdate(
            askReplyTo => Update(dataKey(userId), UserType.create(userId), writeMajority, askReplyTo) {
              user => user
            },
            rsp => InternalUpdateResponse(userId, replyTo, rsp))
          replyTo ! Created(UserId(userId))
          Created

          Behaviors.same
      }

      def receiveOther: PartialFunction[Command, Behavior[Command]] = {
        case InternalUpdateResponse(userId, replyTo, _: UpdateSuccess[_]) =>
          replyTo ! ChangedCreditSucceed
          Behaviors.same
        case InternalUpdateResponse(userId, replyTo, _: UpdateTimeout[_]) =>
          // UpdateTimeout, will eventually be replicated
          replyTo ! ChangedCreditSucceed
          Behaviors.same
        case InternalUpdateResponse(userId, replyTo, e: UpdateFailure[_]) =>
          e match {
            case ModifyFailure(_, _, NotEnoughCreditException(), _) =>
              replyTo ! ChangedCreditFailed
            case _ =>
              replyTo ! Failed("Failure updating " + e)
          }

          Behaviors.same
        case InternalUpdateResponse(userId, replyTo, UpdateDataDeleted(_, _)) =>
          replyTo ! Failed("A deleted key can not be used again: " + userId)
          Behaviors.same
      }

      behavior
    }
  }

}
