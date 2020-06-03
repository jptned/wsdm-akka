package microservice.models

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.HashCodeNoEnvelopeMessageExtractor
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import microservice.setups.Initials.{UserId, UserStorageEntityId}


object UserManager {

  def apply(): Behavior[UserManagerCommand] = Behaviors.setup[UserManagerCommand] { context =>

    // initialize the shading extension
      val sharding = ClusterSharding(context.system)

      // define a message extractor that knows how to retrieve the entityId from a message
      val messageExtractor =
        new HashCodeNoEnvelopeMessageExtractor[UserStorage.UserCommand](numberOfShards = 30) {
          override def entityId(message: UserStorage.UserCommand): String = {
            message.userId.id
//            val split_message = message.userId.id.split("-")
//            split_message[0].toString() + '-' + split_message[1].toString()
          }
        }

      // initialize the shard region
      val shardRegion: ActorRef[UserStorage.UserCommand] =
        sharding.init(
          Entity(UserStorageTypeKey) { context =>
            UserStorage(
              UserStorageEntityId(context.entityId),
              PersistenceId(context.entityTypeKey.name, context.entityId))
          }.withMessageExtractor(messageExtractor)
            // custom stop message to allow for graceful shutdown
            // this is especially important for persistent actors, as the default is PoisonPill,
            // which doesn't allow the actor to flush all messages in flight to the journal
            .withStopMessage(UserStorage.UserGracefulStop))

      Behaviors.receiveMessage {
        case CreateUser(sender) =>
          print("UserManager receives create message.")
          shardRegion ! UserStorage.CreateUser(UserId(UUID.randomUUID().toString), sender)
          Behaviors.same
        case RemoveUser(userId, sender) =>
          shardRegion ! UserStorage.RemoveUser(userId, sender)
          Behaviors.same
        case FindUser(userId, sender) =>
          shardRegion ! UserStorage.FindUser(userId, sender)
          Behaviors.same
        case SubtractCredit(userId, amount, sender) =>
          shardRegion ! UserStorage.SubtractCredit(userId, amount, sender)
          Behaviors.same
        case AddCredit(userId, amount, sender) =>
          shardRegion ! UserStorage.AddCredit(userId, amount, sender)
          Behaviors.same
        case _ => Behaviors.unhandled
      }

    }

  // ~~~ public protocol
  sealed trait UserManagerCommand
  case class CreateUser(sender: ActorRef[UserStorage.UserResponse]) extends UserManagerCommand
  case class RemoveUser(userId: UserId, sender: ActorRef[UserStorage.UserResponse]) extends UserManagerCommand
  case class FindUser(userId: UserId, sender: ActorRef[UserStorage.UserResponse]) extends UserManagerCommand
  case class SubtractCredit(userId: UserId, amount: Long, sender: ActorRef[UserStorage.UserResponse]) extends UserManagerCommand
  case class AddCredit(userId: UserId, amount: Long, sender: ActorRef[UserStorage.UserResponse]) extends UserManagerCommand

  val UserStorageTypeKey:EntityTypeKey[UserStorage.UserCommand] = EntityTypeKey[UserStorage.UserCommand]("UserStorage")
}
