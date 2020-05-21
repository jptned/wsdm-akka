package webshop.order

import java.util.UUID

import akka.util.Timeout
import webshop.stock.Stock
import webshop.stock.Stock.ItemIdentifier

//import akka.actor.typed.ActorRef

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}
import webshop.user.UserRepository.{Command, UserIdentifier}


class OrderHandler(stock: ActorRef) extends PersistentActor with AtLeastOnceDelivery with ActorLogging {

  import OrderHandler._

  override def persistenceId = s"OrderHandler-${self.path.name}"

  var orderClients = Map.empty[OrderIdentifier, ActorRef]
  var orders = Map.empty[OrderIdentifier, StoredOrder]

//  PartialFunction[Receive, Behavior[OrderCommand]]

  override def receiveCommand: Receive = {
    case CreateOrder(userId, replyTo) =>
      val order = StoredOrder(OrderIdentifier.generate, OrderStatus.New, Order(userId, List(), 0))
      replyTo ! GetOrderIdentifierResponse(Some(order.orderId))
      persist(OrderCreated(order))(handleEvent)

    case RemoveOrder(orderId, replyTo) if orders.contains(orderId) =>
      replyTo ! Succeed
      persist(OrderRemoved(orderId))(handleEvent)

    case RemoveOrder(orderId, replyTo) =>
      replyTo ! Failed("This order does not exist.")

    case FindOrder(orderId, replyTo) =>
      replyTo ! GetOrderResponse(orders.get(orderId))

    case AddItemToOrder(orderId, itemId, replyTo) if orders.contains(orderId) =>
      stock ! Stock.GetPriceItem(orderId, itemId, replyTo)

    case AddItemToOrder(orderId, itemId, replyTo) =>
      replyTo ! Failed("This order does not exist.")

    case ReceivedPriceItem(orderId, itemId, price, succeed, replyTo) =>
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
      orders += order.orderId -> order
      if (recoveryFinished) {
        orderClients += order.orderId -> sender()
      }

    case OrderRemoved(orderId) =>
      orders -= orderId

    case ItemAddedToOrder(orderId, itemId, price) =>
      itemId :: orders(orderId).order.items
      orders(orderId).order.totalCost += price

  }

}

object OrderHandler {

//  def props(validator: ActorRef, executor: ActorRef): Props = Props(new OrderHandler.(validator, executor))
  sealed trait OrderCommand

  case class OrderIdentifier(orderId: UUID) extends AnyVal
  object OrderIdentifier {
    def generate = OrderIdentifier(UUID.randomUUID())
  }

  case class Order(userId: UserIdentifier, items: List[ItemIdentifier], totalCost: Long)
  case class StoredOrder(orderId: OrderIdentifier, status: OrderStatus, order: Order)

  case class CreateOrder(userId: UserIdentifier, replyTo: ActorRef) extends OrderCommand
  case class RemoveOrder(orderId: OrderIdentifier, replyTo: ActorRef) extends OrderCommand
  case class FindOrder(orderId: OrderIdentifier, replyTo: ActorRef) extends OrderCommand
  case class AddItemToOrder(orderId: OrderIdentifier, itemId: ItemIdentifier, replyTo: ActorRef) extends OrderCommand
  case class RemoveItemFromOrder(orderId: OrderIdentifier, itemId: ItemIdentifier, replyTo: ActorRef) extends OrderCommand
  case class CheckoutOrder(orderId: OrderIdentifier, replyTo: ActorRef) extends OrderCommand

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
