package webshop.stock

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props, Timers}
import akka.persistence.AtLeastOnceDelivery
//import akka.actor.typed.ActorRef
import akka.actor.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.PersistentActor
import webshop.order.OrderHandler
import webshop.order.OrderHandler.{Order, OrderIdentifier}
import scala.concurrent.duration._

import scala.collection.immutable

//final case class Item(itemId: String, price: Int, stock: Int)
//final case class Stock(items: immutable.Seq[Item])

class Stock() extends PersistentActor with AtLeastOnceDelivery with ActorLogging {


  import Stock._

  context.setReceiveTimeout(1.hour)

  override def persistenceId = s"Stock-${self.path.name}"

  var receivedOrders = Map.empty[OrderIdentifier, LocalDateTime]

  var inventoryClients = Map.empty[ItemIdentifier, ActorRef]
  var inventory = Map.empty[ItemIdentifier, StoredItem]

  override def receiveCommand: Receive = {

    case ExecuteOrder(id, order, deliveryId) =>
      log.info("Received new order for processing")
      if (!receivedOrders.contains(id)) {
        receivedOrders += id -> LocalDateTime.now
        persist(OrderExecuted(id, order))(handleEvent)
      }
//      sender() ! OrderHandler.ConfirmExecuteOrderDelivery(deliveryId, order)
    case GetPriceItem(orderId, itemId, replyTo) if inventory.contains(itemId) =>
      sender() ! OrderHandler.ReceivedPriceItem(orderId, itemId, inventory(itemId).price, succeed = true, replyTo)
    case GetPriceItem(orderId, itemId, replyTo) =>
      sender() ! OrderHandler.ReceivedPriceItem(orderId, itemId, 0, succeed = false, replyTo)
  }

  override def receiveRecover: Receive = {
    case event: StockEvent => handleEvent(event)
  }

  private def handleEvent(event: StockEvent): Unit = event match {
    case OrderExecuted(orderId, order) =>
      println("execute order")
  }
}

object Stock {

  def props(): Props = Props(new Stock)

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
