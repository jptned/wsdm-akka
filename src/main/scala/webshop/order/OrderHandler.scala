package webshop.order

import java.util.UUID

//import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}
import webshop.user.UserRepository.{Command, UserIdentifier}


class OrderHandler() extends PersistentActor with AtLeastOnceDelivery with ActorLogging {

  import OrderHandler._

  var orderClients = Map.empty[OrderIdentifier, ActorRef]
  var orders = Map.empty[OrderIdentifier, StoredOrder]

  override def receiveCommand: Receive = {
    case CreateOrder(userId) =>
      persist(OrderCreated(StoredOrder(OrderIdentifier.generate, OrderStatus.New, Order(userId, List(), 0))))
    case RemoveOrder(orderId) =>
      persist(OrderRemoved(orderId))
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

  }

}

object OrderHandler {

//  def props(validator: ActorRef, executor: ActorRef): Props = Props(new OrderHandler.(validator, executor))
  sealed trait OrderCommand

  case class OrderIdentifier(userId: UUID) extends AnyVal
  object OrderIdentifier {
    def generate = OrderIdentifier(UUID.randomUUID())
  }

  case class Order(userId: UserIdentifier, items: List[ItemIdentifier], totalCost: Long)
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

  sealed trait OrderEvent
  case class OrderCreated(order: StoredOrder) extends OrderEvent
  case class OrderRemoved(orderId: OrderIdentifier) extends OrderEvent
  case class OrderFound(orderId: OrderIdentifier) extends OrderEvent
  case class ItemAddedToOrder(orderId: OrderIdentifier, itemId: ItemIdentifier) extends OrderEvent
  case class ItemRemovedFromOrder(orderId: OrderIdentifier, itemId: ItemIdentifier) extends OrderEvent
  case class OrderCheckedOut(orderId: OrderIdentifier) extends OrderEvent

  case class PayOrderValidated(orderId: OrderIdentifier) extends OrderEvent
  case class PayOrderRejected(orderId: OrderIdentifier) extends OrderEvent
  case class OrderAcquired(orderId: OrderIdentifier) extends OrderEvent
  case class OrderFailed(orderId: OrderIdentifier) extends OrderEvent

}
