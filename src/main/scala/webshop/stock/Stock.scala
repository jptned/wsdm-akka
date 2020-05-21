package webshop.stock

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props, Timers}
//import akka.actor.typed.ActorRef
import akka.actor.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.PersistentActor
import webshop.order.OrderHandler
import webshop.order.OrderHandler.{Order, OrderIdentifier}

import scala.collection.immutable

//final case class Item(itemId: String, price: Int, stock: Int)
//final case class Stock(items: immutable.Seq[Item])

class Stock extends PersistentActor with ActorLogging with Timers {


  import Stock._

  var receivedOrders = Map.empty[OrderIdentifier, LocalDateTime]

  var inventoryClients = Map.empty[ItemIdentifier, ActorRef]
  var inventory = Map.empty[ItemIdentifier, StoredItem]

  def receive: Receive = {

    case ExecuteOrder(id, order, deliveryId) =>
      if (!receivedOrders.contains(id)) {
        receivedOrders += id -> LocalDateTime.now
        persist(OrderExecuted(id, order))(handleEvent)
      }
      sender() ! OrderHandler.ConfirmExecuteOrderDelivery(deliveryId, order)
    case GetPriceItem(orderId, itemId, replyTo) if inventory.contains(itemId) =>
      sender() ! OrderHandler.ReceivedPriceItem(orderId, itemId, inventory(itemId).price, true, replyTo)
    case GetPriceItem(orderId, itemId, replyTo) =>
      sender() ! OrderHandler.ReceivedPriceItem(orderId, itemId, 0, false, replyTo)
  }

  override def receiveRecover: Receive = {
    case event: StockEvent => handleEvent(event)
  }

  private def handleEvent(event: StockEvent): Unit = event match {
    case OrderExecuted(orderId, order) =>


  }
}

object Stock {

//  def props() = Props(new Stock)

  case class ItemIdentifier(orderId: UUID) extends AnyVal
    object OrderIdentifier {
      def generate = ItemIdentifier(UUID.randomUUID())
    }

  case class StoredItem(itemId: ItemIdentifier, stock: Long, price: Long)

  sealed trait StockCommand
  case class ExecuteOrder(id: OrderIdentifier, order: Order, deliveryId: Long) extends StockCommand
  case class GetPriceItem(orderId: OrderIdentifier, itemId: ItemIdentifier, replyTo: ActorRef)

  sealed trait StockEvent
  case class OrderExecuted(orderId: OrderIdentifier, order: Order) extends StockEvent


}

//  sealed trait Command
//  final case class CreateItem(item: Item, replyTo: ActorRef[ActionPerformed]) extends Command
//  final case class GetItem(id: String, replyTo: ActorRef[GetItemResponse]) extends Command
//  final case class DeleteUser(id: String, replyTo: ActorRef[ActionPerformed]) extends Command
//
//  final case class GetItemResponse(maybeItem: Option[Item])
//  final case class ActionPerformed(description: String)
//
//  def apply(): Behavior[Command] = registry(Set.empty)
//
//  private def registry(items: Set[Item]): Behavior[Command] = Behaviors.receiveMessage {
//    case CreateItem(item, replyTo) =>
//      replyTo ! ActionPerformed(s"Item /${item.itemId} created.")
//      registry(items + item)
//    case GetItem(id, replyTo) =>
//      replyTo ! GetItemResponse(items.find(_.itemId == id))
//      Behaviors.same
//    case DeleteUser(id, replyTo) =>
//      replyTo ! ActionPerformed(s"Item /$id deleted.")
//      registry(items.filterNot(_.itemId == id))
//  }
//
//}