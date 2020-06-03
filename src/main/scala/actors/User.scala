package actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Delete, DeleteFailure, Get, Update}
import akka.cluster.ddata.{ReplicatedData, SelfUniqueAddress}
import types.{NotEnoughCreditException, UserType, UserTypeKey}

import scala.concurrent.duration._

object User {

  sealed trait Command

  final case class FindUser(replyTo: ActorRef[UserResponse]) extends Command
  final case class DeleteUser(replyTo: ActorRef[UserResponse]) extends Command
  final case class SubtractCredit(number: Long, replyTo: ActorRef[UserResponse]) extends Command
  final case class AddCredit(number: Long, replyTo: ActorRef[UserResponse]) extends Command
  final case class CreateUser(replyTo: ActorRef[UserResponse]) extends Command

  sealed trait UserResponse

  final case class User(user_id: String, credit: BigInt) extends UserResponse
  final case class Failed(reason: String) extends UserResponse
  final case class NotEnoughCredit() extends UserResponse
  final case class Successful() extends UserResponse

  private sealed trait InternalCommand extends Command

  private case class InternalFindResponse(replyTo: ActorRef[UserResponse], rsp: GetResponse[UserType]) extends InternalCommand
  private case class InternalUpdateResponse[A <: ReplicatedData](replyTo: ActorRef[UserResponse], rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalDeleteResponse[A <: ReplicatedData](replyTo: ActorRef[UserResponse], rsp: DeleteResponse[A]) extends InternalCommand
  private case class InternalCreateResponse(replyTo: ActorRef[UserResponse], getResponse: GetResponse[UserType]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  def apply(user_id: String): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, UserType] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      val DataKey = UserTypeKey("user-" + user_id)

      def behavior = Behaviors.receiveMessagePartial(
        receiveFindUser
          .orElse(receiveDeleteUser)
          .orElse(reveiveSubtractCredit)
          .orElse(receiveAddCredit)
          .orElse(receiveCreateUser)
          .orElse(receiveOther)
      )
      
      def receiveDeleteUser: PartialFunction[Command, Behavior[Command]] = {
        case DeleteUser(replyTo) =>
          replicator.askDelete(
            askReplyTo => Delete(DataKey, writeMajority, askReplyTo),
            rsp => InternalDeleteResponse(replyTo, rsp)
          )
          Behaviors.same
        case InternalDeleteResponse(replyTo, DeleteSuccess(_, _)) =>
          replyTo ! Successful()
          Behaviors.same
        case InternalDeleteResponse(replyTo, e@ReplicationDeleteFailure(_, _)) =>
          replyTo ! Failed("Failed deleting: " + e)
          Behaviors.same
        case InternalDeleteResponse(replyTo, e) =>
          replyTo ! Failed("Failed deleting: " + e)
          Behaviors.same
      }

      def receiveFindUser: PartialFunction[Command, Behavior[Command]] = {
        case FindUser(replyTo) =>
          replicator.askGet(
            askReplyTo => Get(DataKey, readMajority, askReplyTo),
            rsp => InternalFindResponse(replyTo, rsp))

          Behaviors.same

        case InternalFindResponse(replyTo, g@GetSuccess(DataKey, _)) =>
          val user = g.get(DataKey)
          replyTo ! User(user.user_id, user.creditValue)
          Behaviors.same

        case InternalFindResponse(replyTo, NotFound(DataKey, _)) =>
          replyTo ! Failed("Couldn't find " + DataKey)
          Behaviors.same

        case InternalFindResponse(replyTo, GetDataDeleted(DataKey, _)) =>
          replyTo ! Failed("Couldn't find " + DataKey)
          Behaviors.same

        case InternalFindResponse(replyTo, GetFailure(DataKey, _)) =>
          // ReadMajority failure, try again with local read
          replicator.askGet(
            askReplyTo => Get(DataKey, ReadLocal, askReplyTo),
            rsp => InternalFindResponse(replyTo, rsp))

          Behaviors.same
      }

      def reveiveSubtractCredit: PartialFunction[Command, Behavior[Command]] = {
        case SubtractCredit(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, UserType.create(user_id), writeMajority, askReplyTo) {
              user => user.decrement(number)
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveAddCredit: PartialFunction[Command, Behavior[Command]] = {
        case AddCredit(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, UserType.create(user_id), writeMajority, askReplyTo) {
              user => user.increment(number)
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveCreateUser: PartialFunction[Command, Behavior[Command]] = {
        case CreateUser(replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, UserType.create(user_id), writeMajority, askReplyTo) {
              user => user
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveOther: PartialFunction[Command, Behavior[Command]] = {
        case InternalUpdateResponse(replyTo, _: UpdateSuccess[_]) =>
          replyTo ! Successful()
          Behaviors.same
        case InternalUpdateResponse(replyTo, _: UpdateTimeout[_]) =>
          // UpdateTimeout, will eventually be replicated
          replyTo ! Successful()
          Behaviors.same
        case InternalUpdateResponse(replyTo, e: UpdateFailure[_]) =>

          e match {
            case ModifyFailure(_, _, NotEnoughCreditException(), _) =>
              replyTo ! NotEnoughCredit()
            case _ =>
              replyTo ! Failed("Failure updating " + e)
          }

          Behaviors.same
        case InternalUpdateResponse(replyTo, UpdateDataDeleted(DataKey, _)) =>
          replyTo ! Failed("A deleted key can not be used again: " + DataKey)
          Behaviors.same
      }

      behavior
    }
  }

}