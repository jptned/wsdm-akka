package microservice.models

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.HashCodeNoEnvelopeMessageExtractor
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import microservice.setups.Initials.{InventoryEntityId, ItemId}

object InventoryManager {

  def apply(): Behavior[InventoryManagerCommand] = Behaviors.setup[InventoryManagerCommand] { context =>

    // initialize the shading extension
    val sharding = ClusterSharding(context.system)

    // define a message extractor that knows how to retrieve the entityId from a message
    val messageExtractor =
      new HashCodeNoEnvelopeMessageExtractor[Inventory.InventoryCommand](numberOfShards = 30) {
        override def entityId(message: Inventory.InventoryCommand): String = {
          message.itemId.id
//          val split_message = message.itemId.id.split("-")
//          split_message[0].toString() + '-' + split_message[1].toString()
        }
      }

    // initialize the shard region
    val shardRegion: ActorRef[Inventory.InventoryCommand] =
      sharding.init(
        Entity(InventoryTypeKey) { context =>
          Inventory(
            InventoryEntityId(context.entityId),
            PersistenceId(context.entityTypeKey.name, context.entityId))
        }.withMessageExtractor(messageExtractor)
          // custom stop message to allow for graceful shutdown
          // this is especially important for persistent actors, as the default is PoisonPill,
          // which doesn't allow the actor to flush all messages in flight to the journal
          .withStopMessage(Inventory.InventoryGracefulStop))

    Behaviors.receiveMessage {
      case CreateItem(price, sender) =>
        shardRegion ! Inventory.CreateItem(ItemId(UUID.randomUUID().toString), price, sender)
        Behaviors.same
      case FindItem(itemId, sender) =>
        shardRegion ! Inventory.FindItem(itemId, sender)
        Behaviors.same
      case SubtractStock(itemId, amount, sender) =>
        shardRegion ! Inventory.SubtractStock(itemId, amount, sender)
        Behaviors.same
      case AddStock(itemId, amount, sender) =>
        shardRegion ! Inventory.AddStock(itemId, amount, sender)
        Behaviors.same
      case _ => Behaviors.unhandled
    }

  }

  // ~~~ public protocol
  sealed trait InventoryManagerCommand
  case class CreateItem(price: Long, sender: ActorRef[Inventory.InventoryResponse]) extends InventoryManagerCommand
  case class FindItem(itemId: ItemId, sender: ActorRef[Inventory.InventoryResponse]) extends InventoryManagerCommand
  case class SubtractStock(itemId: ItemId, amount: Long, sender: ActorRef[Inventory.InventoryResponse]) extends InventoryManagerCommand
  case class AddStock(itemId: ItemId, amount: Long, sender: ActorRef[Inventory.InventoryResponse]) extends InventoryManagerCommand

  val InventoryTypeKey = EntityTypeKey[Inventory.InventoryCommand]("Inventory")

}
