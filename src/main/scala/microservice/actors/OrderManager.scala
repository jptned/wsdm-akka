package microservice.actors

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.SelfUniqueAddress
import akka.cluster.sharding.typed.HashCodeNoEnvelopeMessageExtractor
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import microservice.actors.OrderActor.OrderId

object OrderManager {

  def apply()(implicit node: SelfUniqueAddress): Behavior[ManagerCommand] =
    Behaviors.setup[ManagerCommand] { context =>
      // initialize the shading extension
      val sharding = ClusterSharding(context.system)

      // define a message extractor that knows how to retrieve the entityId from a message
      val messageExtractor =
      new HashCodeNoEnvelopeMessageExtractor[OrderActor.Command](numberOfShards = 100) {
        override def entityId(message: OrderActor.Command): String = message.orderId.id
      }

      // initialize the shard region
      val shardRegion: ActorRef[OrderActor.Command] =
        sharding.init(
          Entity(OrderRequestTypeKey) { context =>
            OrderActor(
              OrderId(context.entityId),
              PersistenceId(context.entityTypeKey.name, context.entityId))
          }.withMessageExtractor(messageExtractor)
            // GracefulStop is a stop message to allow for graceful shutdown
            // this is especially important for persistent actors, as the default is PoisonPill,
            // which doesn't allow the actor to flush all messages in flight to the journal
            .withStopMessage(OrderActor.GracefulStop))

      Behaviors.receiveMessage {
        case CreateOrder(userId, sender) =>
          val orderId = OrderId(UUID.randomUUID().toString)
          shardRegion ! OrderActor.CreateOrderRequest(orderId, userId, sender)
          Behaviors.same
        case RemoveOrder(orderId, sender) =>
          shardRegion ! OrderActor.RemoveOrderRequest(orderId, sender)
          Behaviors.same
        case FindOrder(orderId, sender) =>
          shardRegion ! OrderActor.FindOrderRequest(orderId, sender)
          Behaviors.same
        case AddItemToOrder(orderId, itemId, sender) =>
          shardRegion ! OrderActor.AddItemToOrderRequest(orderId, itemId, sender)
          Behaviors.same
        case RemoveItemFromOrder(orderId, itemId, sender) =>
          shardRegion ! OrderActor.RemoveItemFromOrderRequest(orderId, itemId, sender)
          Behaviors.same
        case CheckoutOrder(orderId, sender) =>
          shardRegion ! OrderActor.CheckoutOrderRequest(orderId, sender)
          Behaviors.same
        case GetPaymentStatus(orderId, sender) =>
          shardRegion ! OrderActor.GetPaymentStatus(orderId, sender)
          Behaviors.same
        case CancelPayment(orderId, userId, sender) =>
          shardRegion ! OrderActor.CancelPayment(orderId, userId, sender)
          Behaviors.same
        case _ => Behaviors.unhandled
      }

    }

  // ~~~ public protocol
  sealed trait ManagerCommand
  case class CreateOrder(userId: String, sender: ActorRef[OrderActor.Response]) extends ManagerCommand
  case class RemoveOrder(orderId: OrderId, sender: ActorRef[OrderActor.Response]) extends ManagerCommand
  case class FindOrder(orderId: OrderId, sender: ActorRef[OrderActor.Response]) extends ManagerCommand
  case class AddItemToOrder(orderId: OrderId, itemId: String, sender: ActorRef[OrderActor.Response]) extends ManagerCommand
  case class RemoveItemFromOrder(orderId: OrderId, itemId: String, sender: ActorRef[OrderActor.Response]) extends ManagerCommand
  case class CheckoutOrder(orderId: OrderId, sender: ActorRef[OrderActor.Response]) extends ManagerCommand

  case class GetPaymentStatus(orderId: OrderId, sender: ActorRef[OrderActor.Response]) extends ManagerCommand
  case class CancelPayment(orderId: OrderId, userId: String, sender: ActorRef[OrderActor.Response]) extends ManagerCommand

  val OrderRequestTypeKey: EntityTypeKey[OrderActor.Command] = EntityTypeKey[OrderActor.Command]("OrderRequest")

}
