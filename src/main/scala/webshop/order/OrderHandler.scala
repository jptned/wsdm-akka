package webshop.order

import java.util.UUID

import akka.util.Timeout
import webshop.stock.Stock
import webshop.stock.Stock.ItemIdentifier

import scala.concurrent.Future

//import akka.actor.typed.ActorRef

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}
import webshop.user.UserRepository.{Command, UserIdentifier}
import scala.concurrent.duration._


class OrderHandler(stock: ActorRef) extends PersistentActor with AtLeastOnceDelivery with ActorLogging {

  import OrderHandler._

  override def persistenceId = s"OrderHandler-${self.path.name}"

  var orderClients = Map.empty[OrderIdentifier, ActorRef]
  var orders = Map.empty[OrderIdentifier, StoredOrder]

  context.setReceiveTimeout(1.hour)

//  PartialFunction[Receive, Behavior[OrderCommand]]

  override def receiveCommand: Receive = {
    case CreateOrder(userId) =>
      log.info("Received new order for processing")
      println("userId " + userId)
      val order = StoredOrder(OrderIdentifier.generate, OrderStatus.New, Order(userId, List(), 0))
      sender() ! GetOrderIdentifierResponse(Some(order.orderId))
      persist(OrderCreated(order))(handleEvent)

    case RemoveOrder(orderId) if orders.contains(orderId) =>
      log.info("Received new order for processing")
      sender() ! Succeed
      persist(OrderRemoved(orderId))(handleEvent)

    case RemoveOrder(_) =>
      log.info("Received new order for processing")
      sender() ! Failed("This order does not exist.")

    case FindOrder(orderId) =>
      log.info("Received new order for processing")
      sender() ! GetOrderResponse(orders.get(orderId))

    case AddItemToOrder(orderId, itemId) if orders.contains(orderId) =>
      stock ! Stock.GetPriceItem(orderId, itemId, sender())

    case AddItemToOrder(orderId, itemId) =>
      log.info("Received new order for processing")
      sender() ! Failed("This order does not exist.")

    case ReceivedPriceItem(orderId, itemId, price, succeed, replyTo) =>
      log.info("Received new order for processing")
      if (succeed) {
        replyTo ! Succeed
        persist(ItemAddedToOrder(orderId, itemId, price))(handleEvent)
      } else {
        replyTo ! Failed("Item does not exist in the inventory.")
      }

  }

  override def receiveRecover: Receive = {
    case event: OrderEvent => handleEvent(event)
  }

  private def handleEvent(event: OrderEvent): Unit = event match {
    case OrderCreated(order) =>
      println("orders " + orders.toList)
      orders += order.orderId -> order
      println("orders " + orders.toList)
      if (recoveryFinished) {
        orderClients += order.orderId -> sender()
      }

    case OrderRemoved(orderId) =>
      println("orders " + orders.toList)
      orders -= orderId
      println("orders " + orders.toList)

    case ItemAddedToOrder(orderId, itemId, price) =>
      itemId :: orders(orderId).order.items
      orders(orderId).order.totalCost += price

  }

}

object OrderHandler {

  def props(stock: ActorRef): Props = Props(new OrderHandler(stock))

  sealed trait OrderCommand

  case class OrderIdentifier(orderId: UUID) extends AnyVal
  object OrderIdentifier {
    def generate = OrderIdentifier(UUID.randomUUID())
  }

  case class Order(userId: UserIdentifier, var items: List[ItemIdentifier], var totalCost: Long)
  case class StoredOrder(orderId: OrderIdentifier, status: OrderStatus, order: Order)

  case class CreateOrder(userId: UserIdentifier) extends OrderCommand
  case class RemoveOrder(orderId: OrderIdentifier) extends OrderCommand
  case class FindOrder(orderId: OrderIdentifier) extends OrderCommand
  case class AddItemToOrder(orderId: OrderIdentifier, itemId: ItemIdentifier) extends OrderCommand
  case class RemoveItemFromOrder(orderId: OrderIdentifier, itemId: ItemIdentifier) extends OrderCommand
  case class CheckoutOrder(orderId: OrderIdentifier) extends OrderCommand

  case class ValidatePayOrder(orderId: OrderIdentifier) extends OrderCommand
  case class RejectPayOrder(orderId: OrderIdentifier) extends OrderCommand
  case class AcquireOrder(orderId: OrderIdentifier) extends OrderCommand
  case class FailOrder(orderId: OrderIdentifier) extends OrderCommand

  case class ReceivedPriceItem(orderId: OrderIdentifier, itemId: ItemIdentifier, price: Long, succeed: Boolean, replyTo: ActorRef) extends OrderCommand

  sealed trait OrderEvent
  case class OrderCreated(order: StoredOrder) extends OrderEvent
  case class OrderRemoved(orderId: OrderIdentifier) extends OrderEvent
  case class ItemAddedToOrder(orderId: OrderIdentifier, itemId: ItemIdentifier, price: Long) extends OrderEvent
  case class ItemRemovedFromOrder(orderId: OrderIdentifier, itemId: ItemIdentifier) extends OrderEvent
  case class OrderCheckedOut(orderId: OrderIdentifier) extends OrderEvent

  case class PayOrderValidated(orderId: OrderIdentifier) extends OrderEvent
  case class PayOrderRejected(orderId: OrderIdentifier) extends OrderEvent
  case class OrderAcquired(orderId: OrderIdentifier) extends OrderEvent
  case class OrderFailed(orderId: OrderIdentifier) extends OrderEvent

  // Trait defining successful and failure responses
  sealed trait OrderResponse
  case object Succeed extends OrderResponse
  final case class Failed(reason: String) extends OrderResponse

  final case class GetOrderIdentifierResponse(identifier: Option[OrderIdentifier])
  final case class GetOrderResponse(maybeOrder: Option[StoredOrder])

}
