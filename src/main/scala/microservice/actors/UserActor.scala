package microservice.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Delete, Get, Update}
import akka.cluster.ddata.{ReplicatedData, SelfUniqueAddress}
import microservice.types.{NotEnoughCreditException, UserType, UserTypeKey}

import scala.concurrent.duration._

object UserActor {

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

  private val timeout = 100.millis
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  def apply(user_id: String)(implicit node: SelfUniqueAddress): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, UserType] { replicator =>
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
          Behaviors.stopped
        case InternalDeleteResponse(replyTo, e) =>
          replyTo ! Failed("Failed deleting: " + e.key)
          Behaviors.stopped
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
          Behaviors.stopped

        case InternalFindResponse(replyTo, _) =>
          replyTo ! Failed("Couldn't find " + DataKey)
          Behaviors.stopped
      }

      def reveiveSubtractCredit: PartialFunction[Command, Behavior[Command]] = {
        case SubtractCredit(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, writeMajority, askReplyTo) {
              case Some(user) => user.decrement(number)
              case None => throw new Exception("User does not exist")
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveAddCredit: PartialFunction[Command, Behavior[Command]] = {
        case AddCredit(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, writeMajority, askReplyTo) {
              case Some(user) => user.increment(number)
              case None => throw new Exception("User does not exist")
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveCreateUser: PartialFunction[Command, Behavior[Command]] = {
        case CreateUser(replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, writeMajority, askReplyTo) {
              case Some(_) => throw new Exception("User already exists")
              case None => UserType.create(user_id)
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveOther: PartialFunction[Command, Behavior[Command]] = {
        case InternalUpdateResponse(replyTo, _: UpdateSuccess[_]) =>
          replyTo ! Successful()
          Behaviors.stopped
        case InternalUpdateResponse(replyTo, _: UpdateTimeout[_]) =>
          // UpdateTimeout, will eventually be replicated
          replyTo ! Successful()
          Behaviors.stopped
        case InternalUpdateResponse(replyTo, e: UpdateFailure[_]) =>

          e match {
            case ModifyFailure(_, _, NotEnoughCreditException(), _) =>
              replyTo ! NotEnoughCredit()
            case _ =>
              replyTo ! Failed("Failure updating " + e)
          }

          Behaviors.stopped
        case InternalUpdateResponse(replyTo, UpdateDataDeleted(DataKey, _)) =>
          replyTo ! Failed("A deleted key can not be used again: " + DataKey)
          Behaviors.stopped
      }

      behavior
    }
  }

}