package webstore

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import webstore.OrderStorage.OrderId
import webstore.UserStorage.UserId

object OrderRequestHandler {
  def apply(orderId: OrderId,
            persistenceId: PersistenceId,
            userStorage: ActorRef[UserStorage.Command[_]],
            itemStorage: ActorRef[ItemStorage.Command[_]]): Behavior[Command] = Behaviors.setup { context =>

    val itemPriceAdapter: ActorRef[ItemStorage.ItemStorageResponse] = context.messageAdapter { response =>
      AdaptedItemPriceResponse(orderId, response)
    }

    val paymentAdapter: ActorRef[Payment.PaymentResponse] = context.messageAdapter { response =>
      AdaptedPaymentResponse(orderId, response)
    }

    val inventoryAdapter: ActorRef[ItemStorage.ItemStorageResponse] = context.messageAdapter { response =>
      AdaptedInventoryResponse(orderId, response)
    }

    def commandHandler(state: State, command: Command): Effect[Event, State] = state match {
      case Empty =>
        command match {
          case CreateOrderRequest(id, userId, replyTo) =>
            Effect
              .persist[Event, State](CreateOrderRequestReceived(id, userId, replyTo)).thenRun { _ =>
              replyTo ! OrderCreatedResponse(id)
            }.thenStop()
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }

      case orderCreated: OrderCreated =>
        command match {
          case RemoveOrderRequest(id, replyTo) =>
            Effect.persist[Event, State](RemoveOrderRequestReceived).thenRun { _ =>
              replyTo ! Succeed
            }.thenStop()
          case FindOrderRequest(id, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
                replyTo ! FindOrderResponse(orderCreated.order)
              }
          case AddItemToOrderRequest(id, itemId, replyTo) =>
            Effect.persist(AddItemToOrderRequestReceived(itemId, replyTo)).thenRun { _ =>
              itemStorage ! ItemStorage.RetrievePrice(itemId, itemPriceAdapter)
            }
          case RemoveItemFromOrderRequest(id, itemId, replyTo) =>
            if (orderCreated.items.contains(itemId)) {
              Effect.persist[Event, State](ItemRemovedFromOrder(itemId)).thenRun { _ =>
                replyTo ! Succeed
              }
            } else {
              Effect.none[Event, State].thenRun { _ =>
                replyTo ! Failed("Item does not exist in the stored list of items.")
              }
            }
          case AdaptedItemPriceResponse(_, item: ItemStorage.ItemFound) =>
            Effect.persist[Event, State](ItemAddedToOrder(item.itemId, item.price)).thenRun { _ =>
              orderCreated.client ! Succeed
            }
          case AdaptedItemPriceResponse(_, ItemStorage.ItemNotFound) =>
            Effect.none[Event, State].thenRun { _ =>
              orderCreated.client ! Failed("Item does not exist in the stock")
            }
          case CheckoutOrderRequest(id, replyTo) =>
            Effect.persist(CheckOutOrderRequestReceived(replyTo)).thenRun { _ =>
              userStorage ! Payment.ProcessPayment(orderCreated.order.userId, orderCreated.order.totalCost,
                paymentAdapter)
            }

        }

      case paymentProcess: ProcessingPayment =>
        command match {
          case AdaptedPaymentResponse(_, UserStorage.PaymentSucceed) =>
            Effect.persist[Event, State](ProcessedPayment).thenRun { _ =>
              userStorage ! UserStorage.RetrievePrice(paymentProcess.order.items, inventoryAdapter)
            }
          case AdaptedPaymentResponse(_, UserStorage.PaymentFailed) =>
            Effect.none[Event, State].thenRun { _ =>
                context.log.warn("Cannot handle request since the user has not enough money."
                  .format(orderId.id))
                paymentProcess.client ! Failed("The user has not enough credit.")
              }
              .thenStop
          case AdaptedPaymentResponse(_, _) =>
            Effect.unhandled
          case GracefulStop => Effect.stop[Event, State]
          case _ =>
            Effect.unhandled
        }

      case inventoryProcess: ProcessingInventory =>
        command match {
          case AdaptedInventoryResponse(_, ItemStorage.InventoryUpdatedSucceed) =>
            Effect.persist[Event, State](ProcessedInventory)
              .thenRun { _ =>
                inventoryProcess.client ! Succeed
              }
              .thenStop()
          case AdaptedInventoryResponse(_, ItemStorage.InventoryUpdatedFailed) =>
            Effect.persist[Event, State](ProcessInventoryFailed).thenRun { _ =>
              context.log.warn("Cannot handle request since the stock fails to subtract all ordered items."
                .format(orderId.id))
              userStorage ! Payment.ProcessPayment(orderCreated.order.userId, orderCreated.order.totalCost,
                paymentAdapter)
            }.thenRun { _ =>
              inventoryProcess.client ! Failed("The stock has not enough items available.")
            }
//          case request: CheckoutOrderRequest =>
//            context.log.info("Repeated checkout request for order {}", orderId)
//            Effect.none.thenRun { _ =>
//              request.replyTo ! Succeed
//            }
          case GracefulStop => Effect.stop[Event, State]
          case _ =>
            Effect.unhandled
        }
      case rollBackProcess: ProcessingRollBackPayment =>
        command match {

        }
    }

    def eventHandler(state: State, event: Event): State = state match {
      case Empty =>
        event match {
          case CreateOrderRequestReceived(id, userId, replyTo) =>
            OrderCreated(id, Order(id, userId, List(), 0, false: Boolean), Map.empty[String, Long], replyTo)
          case _ => Empty
        }

      case orderCreated: OrderCreated =>
        event match {
          case RemoveOrderRequestReceived => Empty
          case AddItemToOrderRequestReceived(itemId, replyTo) =>
            val order = Order(orderCreated.orderId, orderCreated.order.userId, itemId ::orderCreated.order.items,
              orderCreated.order.totalCost, orderCreated.order.paid)
            OrderCreated(orderCreated.orderId, order, orderCreated.items.updated(itemId, 0), replyTo)
          case ItemRemovedFromOrder(itemId) =>
            val nItemWithItemId = orderCreated.order.items.count(_ == itemId)
            val order = Order(orderCreated.orderId, orderCreated.order.userId,
              orderCreated.order.items.filter(_ != itemId),
              orderCreated.order.totalCost - (nItemWithItemId * orderCreated.items(itemId)),
              orderCreated.order.paid)
            OrderCreated(orderCreated.orderId, order, orderCreated.items - itemId, orderCreated.client)
          case ItemAddedToOrder(itemId, price) =>
            val order = Order(orderCreated.orderId, orderCreated.order.userId, orderCreated.order.items,
              orderCreated.order.totalCost + price, orderCreated.order.paid)
            OrderCreated(orderCreated.orderId, order, orderCreated.items.updated(itemId, price), orderCreated.client)
          case CheckOutOrderRequestReceived(replyTo) =>
            ProcessingPayment(orderCreated.orderId, orderCreated.order, replyTo)
          case _ => orderCreated
        }

      case paymentProcess: ProcessingPayment =>
        event match {
          case ProcessedPayment =>
            ProcessingInventory(paymentProcess.orderId, paymentProcess.order, paymentProcess.client)
          case _ => state
        }

      case inventoryProcess: ProcessingInventory =>
        event match {
          case ProcessedInventory =>
            OrderProcessed(inventoryProcess.orderId, inventoryProcess.order, inventoryProcess.client)
          case ProcessInventoryFailed =>
            ProcessingRollBackPayment(inventoryProcess.orderId, inventoryProcess.order, inventoryProcess.client)
          case _ => inventoryProcess
        }

      case rollBackProcess: ProcessingRollBackPayment =>
        event match {
          case ProcessedRollBack =>
            OrderCreated()
          case _ => rollBackProcess
        }

      case processed: OrderProcessed => processed
    }

    EventSourcedBehavior[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = Empty,
      commandHandler = commandHandler,
      eventHandler = eventHandler)
//    ).receiveSignal {
//      case (state: ProcessingPayment, RecoveryCompleted) =>
//        // request configuration again
//        configuration ! Configuration.RetrieveConfiguration(state.merchantId, state.userId, configurationAdapter)
//    }

  }

  case class Order(orderId: OrderId, userId: UserId, var items: List[String], var totalCost: Long, var paid: Boolean)
  // public protocol

  sealed trait Command {
    def orderId: OrderId
  }
  final case class CreateOrderRequest(orderId: OrderId, userId: UserId, replyTo: ActorRef[Response]) extends Command
  final case class CheckoutOrderRequest(orderId: OrderId, replyTo: ActorRef[Response]) extends Command
  final case class RemoveOrderRequest(orderId: OrderId, replyTo: ActorRef[Response]) extends Command
  final case class FindOrderRequest(orderId: OrderId, replyTo: ActorRef[Response]) extends Command
  final case class AddItemToOrderRequest(orderId: OrderId, itemId: String, replyTo: ActorRef[Response]) extends Command
  final case class RemoveItemFromOrderRequest(orderId: OrderId, itemId: String, replyTo: ActorRef[Response]) extends Command

  final case object GracefulStop extends Command {
    // this message is intended to be sent directly from the parent shard, hence the orderId is irrelevant
    override def orderId: OrderId = OrderId("")
  }

  sealed trait Event

  final case class CreateOrderRequestReceived(orderId: OrderId, userId: UserId, replyTo: ActorRef[Response]) extends Event
  final case class CheckOutOrderRequestReceived(replyTo: ActorRef[Response]) extends Event
  final case object RemoveOrderRequestReceived extends Event
  final case class AddItemToOrderRequestReceived(itemId: String, replyTo: ActorRef[Response]) extends Event
  final case class ItemAddedToOrder(itemId: String, price: Long) extends Event
  final case class ItemRemovedFromOrder(itemId: String) extends Event
  final case object ProcessedPayment extends Event
  final case object ProcessedInventory extends Event
  final case object ProcessInventoryFailed extends Event
  final case object ProcessedRollBack extends Event

  sealed trait State

  final case object Empty extends State
  final case class OrderCreated(orderId: OrderId, order: Order, items: Map[String, Long], client: ActorRef[Response]) extends State
  final case class ProcessingPayment(orderId: OrderId, order: Order, client: ActorRef[Response]) extends State
  final case class ProcessingInventory(orderId: OrderId, order: Order, client: ActorRef[Response]) extends State
  final case class ProcessingRollBackPayment(orderId: OrderId, order: Order, client: ActorRef[Response]) extends State
  final case class OrderProcessed(orderId: OrderId, order: Order, client: ActorRef[Response]) extends State

  sealed trait Response
  final case class OrderCreatedResponse(orderId: OrderId) extends Response
  final case class FindOrderResponse(order: Order) extends Response
  case object Succeed extends Response
  final case class Failed(reason: String) extends Response


  // internal protocol
  sealed trait InternalMessage extends Command
  private final case class AdaptedItemPriceResponse(orderId: OrderId, response: ItemStorage.ItemStorageResponse)
    extends InternalMessage
  private final case class AdaptedPaymentResponse(orderId: OrderId, response: Payment.PaymentResponse)
    extends InternalMessage
  private final case class AdaptedInventoryResponse(orderId: OrderId, response: ItemStorage.ItemStorageResponse)
    extends InternalMessage

}

