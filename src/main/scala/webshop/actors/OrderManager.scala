//package webshop.actors
//
//import java.util.UUID
//
//import akka.actor.typed.scaladsl._
//import akka.actor.typed.scaladsl.Behaviors
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.cluster.sharding.typed.HashCodeNoEnvelopeMessageExtractor
//import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
//import akka.persistence.typed.PersistenceId
//import webshop.Initials.{ItemId, OrderId, UserId}
//
//object OrderManager {
//
//  def apply(): Behavior[ManagerCommand] =
//    Behaviors.setup[ManagerCommand] { context =>
//      // initialize the shading extension
//      val sharding = ClusterSharding(context.system)
//
//      // define a message extractor that knows how to retrieve the entityId from a message
//      val messageExtractor =
//      new HashCodeNoEnvelopeMessageExtractor[OrderRequest.Command](numberOfShards = 30) {
//        override def entityId(message: OrderRequest.Command): String = message.orderId.id
//      }
//
//
//      val userActorRouter = context.spawn(
//        Routers.group(CreditCardProcessor.Key),
//        "userActor"
//      )
//
//      val stockActorRouter = context.spawn(
//        Routers.group(CreditCardProcessor.Key),
//        "stockActor"
//      )
//
//      // initialize the shard region
//      val shardRegion: ActorRef[OrderRequest.Command] =
//        sharding.init(
//          Entity(OrderRequestTypeKey) { context =>
//            OrderRequest(
//              OrderId(context.entityId),
//              PersistenceId(context.entityTypeKey.name, context.entityId),
//              orderStorage,
//              paymentHandlerRouter)
//          }.withMessageExtractor(messageExtractor)
//            // custom stop message to allow for graceful shutdown
//            // this is especially important for persistent actors, as the default is PoisonPill,
//            // which doesn't allow the actor to flush all messages in flight to the journal
//            .withStopMessage(OrderRequest.GracefulStop))
//
//      Behaviors.receiveMessage {
//        case CreateOrder(userId, sender) =>
//          shardRegion ! OrderRequest.CreateOrderRequest(OrderId(UUID.randomUUID().toString), userId, sender)
//          Behaviors.same
//        case RemoveOrder(orderId, sender) =>
//          shardRegion ! OrderRequest.RemoveOrderRequest(orderId, sender)
//          Behaviors.same
//        case FindOrder(orderId, sender) =>
//          shardRegion ! OrderRequest.FindOrderRequest(orderId, sender)
//          Behaviors.same
//        case AddItemToOrder(orderId, itemId, sender) =>
//          shardRegion ! OrderRequest.AddItemToOrderRequest(orderId, itemId, sender)
//          Behaviors.same
//        case RemoveItemFromOrder(orderId, itemId, sender) =>
//          shardRegion ! OrderRequest.RemoveItemFromOrderRequest(orderId, itemId, sender)
//          Behaviors.same
//        case CheckoutOrder(orderId, sender) =>
//          shardRegion ! OrderRequest.CheckoutOrderRequest(orderId, sender)
//          Behaviors.same
//        case _ => Behaviors.unhandled
//      }
//
//    }
//
//  // ~~~ public protocol
//  sealed trait ManagerCommand
//  case class CreateOrder(userId: UserId, sender: ActorRef[OrderRequest.Response]) extends ManagerCommand
//  case class RemoveOrder(orderId: OrderId, sender: ActorRef[OrderRequest.Response]) extends ManagerCommand
//  case class FindOrder(orderId: OrderId, sender: ActorRef[OrderRequest.Response]) extends ManagerCommand
//  case class AddItemToOrder(orderId: OrderId, itemId: ItemId, sender: ActorRef[OrderRequest.Response]) extends ManagerCommand
//  case class RemoveItemFromOrder(orderId: OrderId, itemId: ItemId, sender: ActorRef[OrderRequest.Response]) extends ManagerCommand
//  case class CheckoutOrder(orderId: OrderId, sender: ActorRef[OrderRequest.Response]) extends ManagerCommand
//
//  val OrderRequestTypeKey: EntityTypeKey[OrderRequest.Command] = EntityTypeKey[OrderRequest.Command]("OrderRequest")
//
//}