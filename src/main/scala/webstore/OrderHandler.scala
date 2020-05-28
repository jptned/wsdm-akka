package webstore

import java.util.UUID

import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.HashCodeNoEnvelopeMessageExtractor
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import webstore.OrderStorage.OrderId
import webstore.UserStorage.UserId

object OrderHandler {

  def apply(orderStorage: ActorRef[OrderRequestHandler.Command]): Behavior[HandlerCommand] =
    Behaviors.setup[HandlerCommand] { context =>
      // initialize the shading extension
      val sharding = ClusterSharding(context.system)

      // define a message extractor that knows how to retrieve the entityId from a message
      // we plan on deploying on a 3-node cluster, as a rule of thumb there should be 10 times as many
      // shards as there are nodes, hence the numberOfShards value of 30
      val messageExtractor =
      new HashCodeNoEnvelopeMessageExtractor[OrderRequestHandler.Command](numberOfShards = 30) {
        override def entityId(message: OrderRequestHandler.Command): String = message.orderId.id
      }

      // initialize the group router for credit card processors
      val paymentHandlerRouter = context.spawn(
        Routers.group(PaymentHandler.Key),
        "creditCardProcessors"
      )

      // initialize the shard region
      val shardRegion: ActorRef[OrderRequestHandler.Command] =
        sharding.init(
          Entity(OrderRequestHandlerTypeKey) { context =>
            OrderRequestHandler(
              OrderId(context.entityId),
              PersistenceId(context.entityTypeKey.name, context.entityId),
              orderStorage,
              paymentHandlerRouter)
          }.withMessageExtractor(messageExtractor)
            // custom stop message to allow for graceful shutdown
            // this is especially important for persistent actors, as the default is PoisonPill,
            // which doesn't allow the actor to flush all messages in flight to the journal
            .withStopMessage(OrderRequestHandler.GracefulStop))

      Behaviors.receiveMessage {
        case CreateOrder(userId, sender) =>
          shardRegion ! OrderRequestHandler.CreateOrderRequest(OrderId(UUID.randomUUID().toString), userId, sender)
          Behaviors.same
        case RemoveOrder(orderId, sender) =>
          shardRegion ! OrderRequestHandler.RemoveOrderRequest(orderId, sender)
          Behaviors.same
        case FindOrder(orderId, sender) =>
          shardRegion ! OrderRequestHandler.FindOrderRequest(orderId, sender)
          Behaviors.same
        case AddItemToOrder(orderId, itemId, sender) =>
          shardRegion ! OrderRequestHandler.AddItemToOrderRequest(orderId, itemId, sender)
          Behaviors.same
        case RemoveItemFromOrder(orderId, itemId, sender) =>
          shardRegion ! OrderRequestHandler.RemoveItemFromOrderRequest(orderId, itemId, sender)
          Behaviors.same
        case CheckoutOrder(orderId, sender) =>
          shardRegion ! OrderRequestHandler.CheckoutOrderRequest(orderId, sender)
          Behaviors.same
        case _ => Behaviors.unhandled
      }

    }

  // ~~~ public protocol
  sealed trait HandlerCommand
  case class CreateOrder(userId: UserId, sender: ActorRef[OrderRequestHandler.Response]) extends HandlerCommand
  case class RemoveOrder(orderId: OrderId, sender: ActorRef[OrderRequestHandler.Response]) extends HandlerCommand
  case class FindOrder(orderId: OrderId, sender: ActorRef[OrderRequestHandler.Response]) extends HandlerCommand
  case class AddItemToOrder(orderId: OrderId, itemId: String, sender: ActorRef[OrderRequestHandler.Response]) extends HandlerCommand
  case class RemoveItemFromOrder(orderId: OrderId, itemId: String, sender: ActorRef[OrderRequestHandler.Response]) extends HandlerCommand
  case class CheckoutOrder(orderId: OrderId, sender: ActorRef[OrderRequestHandler.Response]) extends HandlerCommand

  val OrderRequestHandlerTypeKey = EntityTypeKey[OrderRequestHandler.Command]("OrderRequestHandler")
}
