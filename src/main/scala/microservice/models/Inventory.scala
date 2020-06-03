package microservice.models

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import microservice.setups.Initials.{InventoryEntityId, Item, ItemId}

object Inventory {

  sealed trait InventoryCommand {
    def itemId: ItemId
  }
  sealed trait InventoryEvent
  sealed trait InventoryResponse

  final case object InventoryGracefulStop extends InventoryCommand {
    // this message is intended to be sent directly from the parent shard, hence the orderId is irrelevant
    override def itemId: ItemId = ItemId("")
  }

  // the protocol for creating an item
  final case class CreateItem(itemId: ItemId, price: Long, replyTo: ActorRef[InventoryResponse]) extends InventoryCommand
  final case class ItemCreated(itemId: ItemId, price: Long) extends InventoryEvent
  case class Created(itemId: ItemId) extends InventoryResponse

  // the protocol for looking up an item by its id
  final case class FindItem(itemId: ItemId, replyTo: ActorRef[InventoryResponse]) extends InventoryCommand
  case class ItemFound(item: Item) extends InventoryResponse
  case class ItemNotFound(Id: ItemId) extends InventoryResponse

  // the protocol for adding and subtracting from the credit of an user by its id
  final case class SubtractStock(itemId: ItemId, amount: Long, replyTo: ActorRef[InventoryResponse]) extends InventoryCommand
  final case class StockSubtracted(itemId: ItemId, amount: Long) extends InventoryEvent
  final case class AddStock(itemId: ItemId, amount: Long, replyTo: ActorRef[InventoryResponse]) extends InventoryCommand
  final case class StockAdded(itemId: ItemId, amount: Long) extends InventoryEvent
  case object ChangedStockSucceed extends InventoryResponse
  case object ChangedStockFailed extends InventoryResponse

  // state definition
  final case class Storage(items: Map[ItemId, Item] = Map.empty) {
    def applyEvent(event: InventoryEvent): Storage = event match {
      case ItemCreated(itemId, price) =>
        copy(items = items.updated(itemId, Item(itemId, price, 0)))
      case StockSubtracted(itemId, amount) =>
        items(itemId).stock -= amount
        copy(items = items.updated(itemId, items(itemId)))
      case StockAdded(itemId, amount) =>
        items(itemId).stock += amount
        copy(items = items.updated(itemId, items(itemId)))

    }

    def applyCommand(context: ActorContext[InventoryCommand], cmd: InventoryCommand):
    ReplyEffect[InventoryEvent, Storage] = cmd match {

      case CreateItem(itemId, price, replyTo) =>
        val event = ItemCreated(itemId, price)
        Effect.persist(event).thenReply(replyTo)(_ => Created(event.itemId))
      case FindItem(id, replyTo) if items.contains(id) =>
        Effect.reply(replyTo)(ItemFound(items(id)))
      case FindItem(id, replyTo) if !items.contains(id) =>
        Effect.reply(replyTo)(ItemNotFound(id))
      case SubtractStock(id, amount, replyTo) if items.contains(id) && items(id).stock - amount < 0 =>
        Effect.reply(replyTo)(ChangedStockFailed)
      case SubtractStock(id, amount, replyTo) if items.contains(id) && items(id).stock - amount >= 0 =>
        val event = StockSubtracted(id, amount)
        Effect.persist(event).thenReply(replyTo)(_ => ChangedStockSucceed)
      case SubtractStock(id, _, replyTo) if !items.contains(id) =>
        Effect.reply(replyTo)(ChangedStockFailed)
      case AddStock(id, amount, replyTo) if items.contains(id) =>
        val event = StockAdded(id, amount)
        Effect.persist(event).thenReply(replyTo)(_ => ChangedStockSucceed)
      case AddStock(id, _, replyTo) if !items.contains(id) =>
        Effect.reply(replyTo)(ChangedStockFailed)
    }
  }

  def apply(inventoryEntityId: InventoryEntityId, persistenceId: PersistenceId): Behavior[InventoryCommand] =
    Behaviors.setup { context =>
    EventSourcedBehavior.withEnforcedReplies[InventoryCommand, InventoryEvent, Storage](
      persistenceId = persistenceId,
      emptyState = Storage(),
      commandHandler = (state, cmd) => state.applyCommand(context, cmd),
      eventHandler = (state, evt) => state.applyEvent(evt))
  }


}
