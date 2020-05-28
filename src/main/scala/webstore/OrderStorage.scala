package webstore

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import webstore.OrderStorage._
import webstore.UserStorage.UserId

class OrderStorage(context: ActorContext[OrderStorageRequest]) extends AbstractBehavior[OrderStorageRequest](context) {


  var orders: Map[OrderId, StoredOrder] = Map.empty

  // the onMessage method defines the initial behavior applied to a message upon reception
  override def onMessage(msg: OrderStorageRequest): Behavior[OrderStorageRequest] = msg match {
    case RetrieveOrder(orderId, replyTo) =>
      orders.get(orderId) match {
        case Some(orderStorage) =>
          replyTo ! OrderFound(orderId, orderStorage)
        case _ =>
          replyTo ! OrderNotFound(orderId)
      }
      this
    case StoreOrder(orderId, order, replyTo) =>
      orders += orderId -> order
      replyTo ! OrderStored(orderId)
      this
  }
}

object OrderStorage {

  def apply(): Behavior[OrderStorageRequest] = Behaviors.setup(context => new OrderStorage(context))

  case class OrderId(id: String) extends AnyVal
  case class Order(userId: UserId, var items: List[String], var totalCost: Long)
  case class StoredOrder(orderId: OrderId, status: String, order: Order)

  sealed trait OrderStorageRequest
  final case class RetrieveOrder(orderId: OrderId, replyTo: ActorRef[OrderStorageResponse]) extends OrderStorageRequest
  final case class StoreOrder(orderId: OrderId, order: StoredOrder, replyTo: ActorRef[OrderStorageResponse]) extends OrderStorageRequest

  sealed trait OrderStorageResponse
  final case class OrderFound(orderId: OrderId, order: StoredOrder) extends OrderStorageResponse
  final case class OrderNotFound(orderId: OrderId) extends OrderStorageResponse
  final case class OrderStored(orderId: OrderId) extends OrderStorageResponse

}