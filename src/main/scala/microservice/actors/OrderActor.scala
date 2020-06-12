package microservice.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import microservice.CborSerializable
import microservice.actors.StockActor


object OrderActor {

  def apply(orderId: OrderId, persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>

    val paymentAdapter: ActorRef[UserActor.UserResponse] = context.messageAdapter { response =>
      AdaptedPaymentResponse(orderId, response)
    }

    val stockAdapter: ActorRef[StockActor.StockResponse] = context.messageAdapter { response =>
      AdaptedStockResponse(orderId, response)
    }

    def commandHandler(state: State, command: Command): Effect[Event, State] = state match {
      // The state is empty: No order is yet created for this entity.
      case Empty =>
        command match {
          // Create a new order, and send a message back with orderId to the client that the order is created.
          case CreateOrderRequest(id, userId, replyTo) =>
            Effect
              .persist[Event, State](CreateOrderRequestReceived(id, userId, replyTo)).thenRun { _ =>
              context.log.info("Create a new order.".format(orderId.id))
              replyTo ! OrderCreatedResponse(id)
            }
          case FindOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot find the order in empty process.".format(orderId.id))
              replyTo ! Failed("Cannot find the order")
            }.thenStop()
          case GetPaymentStatus(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cancel the payment in empty process.".format(orderId.id))
              replyTo ! PaymentStatus(Status(false))
            }.thenStop()
          case CancelPayment(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cancel the payment in empty process.".format(orderId.id))
              replyTo ! Failed("We have not paid yet.")
            }.thenStop()
          case RemoveOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove the order in empty process.".format(orderId.id))
              replyTo ! Failed("Cannot remove the order.")
            }.thenStop()
          case AddItemToOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot add an item to the order in empty process.".format(orderId.id))
              replyTo ! Failed("Cannot add an item to the order.")
            }.thenStop()
          case RemoveItemFromOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove an item from the order in empty process.".format(orderId.id))
              replyTo ! Failed("Cannot remove an item from the order.")
            }.thenStop()
          case CheckoutOrderRequest(_, _) => Effect.none[Event, State]
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }

      // The state is the OrderProcess: an order is created for this entity, and the client can add items to the
      // order; can look for the order; can remove the order, and can checkout the order.
      case process: OrderProcess =>
        command match {
          // Remove the order, send a message back to the client that the order is removed, and stop the entity.
          case RemoveOrderRequest(_, replyTo) =>
            Effect.persist[Event, State](RemoveOrderRequestReceived).thenRun { _ =>
              context.log.info("Remove the order.".format(orderId.id))
              replyTo ! Succeed
            }.thenStop()
          // Find the order, and send a message to the client with the order.
          case FindOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Look for the order.".format(orderId.id))
              replyTo ! FindOrderResponse(process.order)
            }
          // Add an item to the order, and wait for a message from the stock.
          case AddItemToOrderRequest(_, itemId, replyTo) =>
            Effect.persist(AddItemToOrderRequestReceived(itemId, replyTo)).thenRun { _ =>
              context.log.info("Request the price of item " + itemId + " by spawning a stock actor.".format(orderId.id))
              // Spawn a new stock actor, and send a message to the stock actor to obtain the stock price.
              val stockActor = context.spawn(StockActor(itemId), "stockActor-" + itemId + "-" + orderId.id)
              stockActor ! StockActor.FindStock(stockAdapter)
            }
          // Remove an item from the order, and send the client a succeed or failed message.
          case RemoveItemFromOrderRequest(_, itemId, replyTo) =>
            // Check whether the item is in the list of items. Otherwise send back a failed message.
            if (process.items.contains(itemId)) {
              Effect.persist[Event, State](ItemRemovedFromOrder(itemId)).thenRun { _ =>
                context.log.info("Item " + itemId + " is successful removed from the list of items ".format(orderId.id))
                replyTo ! Succeed
              }
            } else {
              Effect.none[Event, State].thenRun { _ =>
                context.log.info("Item " + itemId + " does not exist in the list of items ".format(orderId.id))
                replyTo ! Failed("Item does not exist in the stored list of items.")
              }
            }
          // Receive an AdaptedStockResponse from the stock which includes the stock.
          case AdaptedStockResponse(_, response: StockActor.Stock) =>
            Effect.persist[Event, State](ItemAddedToOrder(response.item_id, response.price)).thenRun { _ =>
              context.log.info("Received the item price from the stock of item " + response.item_id.format(orderId.id))
              process.client ! Succeed
            }
          // Receive an AdaptedStockResponse from the stock which includes a failed response.
          case AdaptedStockResponse(_, response: StockActor.Failed) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Failed to get the item price from the stock".format(orderId.id))
              process.client ! Failed(response.reason)
            }
          // Checkout the order by sending a payment request to the user.
          case CheckoutOrderRequest(_, replyTo) =>
            Effect.persist(CheckOutOrderRequestReceived(replyTo)).thenRun { _ =>
              context.log.info("Checkout the order.".format(orderId.id))
              val userActor = context.spawn(UserActor(process.order.userId),
                "userActor-" + process.order.userId + "-" + orderId.id)
              userActor ! UserActor.SubtractCredit(process.order.totalCost, paymentAdapter)
            }
          // Obtain the payment status
          case GetPaymentStatus(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! PaymentStatus(Status(process.order.paid))
            }
          // Cancel the payment
          case CancelPayment(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! Failed("We have not paid yet.")
            }
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }
      // The state is the PaymentProcess. In this state, we wait for a message from the UserActor to
      // proceed the checkout steps.
      case process: PaymentProcess =>
        command match {
          // The entity receives a response that the payment is succeeded, so the order actor sends a message to
          // all stock actor for each item, it has in its item list.
          case AdaptedPaymentResponse(_, UserActor.Successful()) =>
            Effect.persist[Event, State](PaymentProcessed(process.order.items.length)).thenRun { _ =>
              context.log.info("Receive a succeed message from the user that the payment is succeed.".format(orderId.id))
              // Check whether there are items in the order list of items
              if (process.order.items.isEmpty) {
                process.client ! Succeed
              } else {
                process.order.items.foreach { itemId =>
                  val stockActor = context.spawn(StockActor(itemId), "stockActor-" + itemId + "-" + orderId.id)
                  stockActor ! StockActor.SubtractStock(1, stockAdapter)
                }
              }
            }
          // The entity receives a response that the payment is failed, so it sends back a failed message to the client.
          case AdaptedPaymentResponse(_, UserActor.Failed(reason)) =>
            Effect.persist[Event, State](BackToOrderProcess).thenRun { _ =>
              context.log.info(reason.format(orderId.id))
              process.client ! Failed(reason)
            }.thenStop
          case AdaptedPaymentResponse(_, UserActor.NotEnoughCredit()) =>
            Effect.persist[Event, State](BackToOrderProcess).thenRun { _ =>
              context.log.info("Not enough credit".format(orderId.id))
              process.client ! Failed("Not enough credit")
            }.thenStop
          case AdaptedPaymentResponse(_, _) => Effect.unhandled
          case FindOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Find the order in payment process.".format(orderId.id))
              replyTo ! FindOrderResponse(process.order)
            }
          case GetPaymentStatus(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Get payment status in payment process.".format(orderId.id))
              replyTo ! PaymentStatus(Status(process.order.paid))
            }
          case CancelPayment(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cancel the payment in payment process.".format(orderId.id))
              replyTo ! Failed("We have not paid yet.")
            }
          case RemoveOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove the order in payment process.".format(orderId.id))
              replyTo ! Failed("Cannot remove the order")
            }
          case AddItemToOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot add an item to the order in payment process.".format(orderId.id))
              replyTo ! Failed("Cannot add an item to the order.")
            }
          case RemoveItemFromOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove an item from the order in payment process.".format(orderId.id))
              replyTo ! Failed("Cannot remove an item from the order.")
            }
          case CheckoutOrderRequest(_, _) => Effect.none[Event, State]
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }
      // The state is the StockProcess. In this state, we wait for all messages from the stock actor to
      // proceed the checkout steps.
      case process: StockProcess =>
        command match {
          // The entity receives a succeed message from the stock that the stock is decremented. The entity waits
          // for success message  before it enters the next state
          case AdaptedStockResponse(_, StockActor.Successful(itemId)) =>
            val succeedResponses = itemId :: process.succeedResponses
              Effect.persist[Event, State](StockProcessed(succeedResponses, process.failedResponses))
              .thenRun { _ =>
                context.log.info("Receive a succeed message from the stock.".format(orderId.id))
                // Check whether we obtain all succeeded responses.
                if (succeedResponses.length == process.expectedResponses) {
                  process.client ! Succeed
                } else if (succeedResponses.length + process.failedResponses.length == process.expectedResponses) {
                  // Entity does not receives all succeed message, so it rolls back the stock process
                  succeedResponses.foreach { itemId =>
                    val stockActor = context.spawn(StockActor(itemId), "stockActor-" + itemId + "-" + orderId.id)
                    stockActor ! StockActor.AddStock(1, stockAdapter)
                  }
                }
              }
          case AdaptedStockResponse(_, StockActor.NotEnoughStock(itemId)) =>
            val failedResponses = itemId :: process.failedResponses

            Effect.persist[Event, State](StockProcessed(process.succeedResponses, failedResponses)).thenRun { _ =>
              context.log.info("Cannot handle request since the stock fails to subtract item " + itemId + "from order "
                .format(orderId.id))
              process.client ! Failed("The stock has not enough items available for item " + itemId + ".")
            }.thenRun { _ =>
              // Entity does not receives all succeed message, so it rolls back the stock process
              if (process.succeedResponses.length + failedResponses.length == process.expectedResponses) {
                if (process.succeedResponses.nonEmpty) {
                  process.succeedResponses.foreach { itemId =>
                    val stockActor = context.spawn(StockActor(itemId), "stockActor-" + itemId + "-" + orderId.id)
                    stockActor ! StockActor.AddStock(1, stockAdapter)
                  }
                } else {
                  val userActor = context.spawn(UserActor(process.order.userId),
                    "userActor-" + process.order.userId + "-" + orderId.id)
                  userActor ! UserActor.AddCredit(process.order.totalCost, paymentAdapter)
                }
              }
            }
          case AdaptedStockResponse(_, StockActor.Failed(reason, itemId)) =>
            val failedResponses = itemId :: process.failedResponses

            Effect.persist[Event, State](StockProcessed(process.succeedResponses, failedResponses)).thenRun { _ =>
              context.log.info("Cannot handle request since the stock fails to subtract item " + itemId + "from order "
                .format(orderId.id))
              process.client ! Failed(reason)
            }.thenRun { _ =>
              // Entity does not receives all succeed message, so it rolls back the stock process
              if (process.succeedResponses.length + failedResponses.length == process.expectedResponses) {
                if (process.succeedResponses.nonEmpty) {
                  process.succeedResponses.foreach { itemId =>
                    val stockActor = context.spawn(StockActor(itemId), "stockActor-" + itemId + "-" + orderId.id)
                    stockActor ! StockActor.AddStock(1, stockAdapter)
                  }
                } else {
                  val userActor = context.spawn(UserActor(process.order.userId),
                    "userActor-" + process.order.userId + "-" + orderId.id)
                  userActor ! UserActor.AddCredit(process.order.totalCost, paymentAdapter)
                }
              }
            }
          case AdaptedStockResponse(_, _) => Effect.unhandled
          case FindOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! FindOrderResponse(process.order)
            }
          case GetPaymentStatus(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Get payment status in stock process.".format(orderId.id))
              replyTo ! PaymentStatus(Status(process.order.paid))
            }
          case CancelPayment(_, _, replyTo) =>
            Effect.persist[Event, State](CancelPaymentProcessed(replyTo)).thenRun { _ =>
                context.log.info("cancel the payment.".format(orderId.id))
                val userActor = context.spawn(UserActor(process.order.userId),
                  "userActor-" + process.order.userId + "-" + orderId.id)
                userActor ! UserActor.AddCredit(process.order.totalCost, paymentAdapter)
            }
          case RemoveOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove the order in stock process.".format(orderId.id))
              replyTo ! Failed("Cannot remove the order")
            }
          case AddItemToOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot add an item to the order in stock process.".format(orderId.id))
              replyTo ! Failed("Cannot add an item to the order.")
            }
          case RemoveItemFromOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove an item from the order in stock process.".format(orderId.id))
              replyTo ! Failed("Cannot remove an item from the order.")
            }
          case CheckoutOrderRequest(_, _) => Effect.none[Event, State]
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }
      // The state is the RollBackStockProcess. In this state, we roll back the stock message, so we increment the stock
      // for items which where successful decremented.
      case process: RollBackStockProcess =>
        command match {
          case AdaptedStockResponse(_, _) =>
            val receivedResponses = process.receivedResponses + 1
            Effect.persist[Event, State](RollBackStockProcessed(receivedResponses)).thenRun { _ =>
                context.log.info("Receive a succeed message from the stock in the rollback process.".format(orderId.id))
                if (process.expectedResponses == receivedResponses) {
                  val userActor = context.spawn(UserActor(process.order.userId),
                    "userActor-" + process.order.userId + "-" + orderId.id)
                  userActor ! UserActor.AddCredit(process.order.totalCost, paymentAdapter)
                }
              }
          case FindOrderRequest(_, replyTo) =>
            context.log.info("Find order in rollback stock process.".format(orderId.id))
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! FindOrderResponse(process.order)
            }
          case GetPaymentStatus(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Get payment status in rollback stock process.".format(orderId.id))
              replyTo ! PaymentStatus(Status(process.order.paid))
            }
          case CancelPayment(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cancel the payment in payment process.".format(orderId.id))
              replyTo ! Failed("We have not paid yet.")
            }
          case RemoveOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove the order in rollback stock process.".format(orderId.id))
              replyTo ! Failed("Cannot remove the order.")
            }
          case AddItemToOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot add an item to the order in rollback stock process.".format(orderId.id))
              replyTo ! Failed("Cannot add an item to the order.")
            }
          case RemoveItemFromOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove an item from the order in rollback stock process.".format(orderId.id))
              replyTo ! Failed("Cannot remove an item from the order.")
            }
          case CheckoutOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Checkout the order in rollback stock process.".format(orderId.id))
              replyTo ! Failed("Checkout the order.")
            }
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }
      // The state is the RollBackPaymentProcess. In this state, we roll back the payment made, so we increment
      // the user's credit.
      case process: RollBackPaymentProcess =>
        command match {
          case AdaptedPaymentResponse(_, _) =>
            Effect.persist[Event, State](RollBackPaymentProcessed).thenRun { _ =>
              context.log.info("Receive a succeed message from the user payment is correctly changed.".format(orderId.id))
            }
          case FindOrderRequest(_, replyTo) =>
            context.log.info("Find order in rollback payment process.".format(orderId.id))
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! FindOrderResponse(process.order)
            }
          case GetPaymentStatus(_, replyTo) =>
            context.log.info("Get payment status in rollback payment process.".format(orderId.id))
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! PaymentStatus(Status(process.order.paid))
            }
          case CancelPayment(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cancel the payment in rollback payment process.".format(orderId.id))
              replyTo ! Failed("We have not paid yet.")
            }
          case RemoveOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove the order in rollback payment process.".format(orderId.id))
              replyTo ! Failed("Cannot remove the order")
            }
          case AddItemToOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot add an item to the order in rollback payment process.".format(orderId.id))
              replyTo ! Failed("Cannot add an item to the order.")
            }
          case RemoveItemFromOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove an item from the order in rollback payment process.".format(orderId.id))
              replyTo ! Failed("Cannot remove an item from the order.")
            }
          case CheckoutOrderRequest(_, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Checkout the order in rollback payment process.".format(orderId.id))
              replyTo ! Failed("Checkout the order.")
            }
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }

      case process: OrderProcessed =>
        command match {
          case CheckoutOrderRequest(_, replyTo) =>
            context.log.info("Checkout order in order processed.".format(orderId.id))
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! Succeed
            }
          case FindOrderRequest(_, replyTo) =>
            context.log.info("Find order in order processed.".format(orderId.id))
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! FindOrderResponse(process.order)
            }
          case RemoveOrderRequest(_, replyTo) =>
            context.log.info("Remove order in order processed.".format(orderId.id))
            Effect.persist[Event, State](RemoveOrderRequestReceived).thenRun { _ =>
              replyTo ! Succeed
            }.thenStop()
          case GetPaymentStatus(_, replyTo) =>
            context.log.info("Get payment status of order in order processed.".format(orderId.id))
            Effect.none[Event, State].thenRun { _ =>
              replyTo ! PaymentStatus(Status(process.order.paid))
            }
          case CancelPayment(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cancel the payment in order processed.".format(orderId.id))
              replyTo ! Failed("We have already paid, which you cannot cancel.")
            }
          case AddItemToOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot add an item to the order in order processed.".format(orderId.id))
              replyTo ! Failed("Cannot add an item to the order.")
            }
          case RemoveItemFromOrderRequest(_, _, replyTo) =>
            Effect.none[Event, State].thenRun { _ =>
              context.log.info("Cannot remove an item from the order in order processed.".format(orderId.id))
              replyTo ! Failed("Cannot remove an item from the order.")
            }
          case GracefulStop => Effect.stop[Event, State]
          case _ => Effect.unhandled
        }

    }

    def eventHandler(state: State, event: Event): State = state match {
      case Empty =>
        event match {
          case CreateOrderRequestReceived(id, userId, replyTo) =>
            OrderProcess(id, Order(id, userId, List(), 0, false: Boolean), Map.empty[String, Long], replyTo)
          case _ => Empty
        }

      case process: OrderProcess =>
        event match {
          case RemoveOrderRequestReceived => Empty
          case AddItemToOrderRequestReceived(_, replyTo) =>
            OrderProcess(process.orderId, process.order, process.items, replyTo)
          case ItemRemovedFromOrder(itemId) =>
            val nItemWithItemId = process.order.items.count(_ == itemId)
            val order = Order(process.orderId, process.order.userId, process.order.items.filter(_ != itemId),
              process.order.totalCost - (nItemWithItemId * process.items(itemId)), process.order.paid)
            OrderProcess(process.orderId, order, process.items - itemId, process.client)
          case ItemAddedToOrder(itemId, price) =>
            val order = Order(process.orderId, process.order.userId, itemId ::process.order.items,
              process.order.totalCost + price, process.order.paid)
            OrderProcess(process.orderId, order, process.items.updated(itemId, price), process.client)
          case CheckOutOrderRequestReceived(replyTo) =>
            PaymentProcess(process.orderId, process.order, process.items, replyTo)
          case _ => process
        }

      case process: PaymentProcess =>
        event match {
          case PaymentProcessed(expectedResponses) => expectedResponses match {
            case 0 =>
              val order = Order(process.orderId, process.order.userId, process.order.items, process.order.totalCost,
                true: Boolean)
              OrderProcessed(process.orderId, order)
            case _ => StockProcess(process.orderId, process.order, expectedResponses, List(), List(), process.items, process.client)
          }
          case BackToOrderProcess =>
            OrderProcess(process.orderId, process.order, process.items, process.client)
          case _ => state
        }

      case process: StockProcess =>
        event match {
          case StockProcessed(succeedResponses, failedResponses) =>

            if (succeedResponses.length == process.expectedResponses) {
              val order = Order(process.orderId, process.order.userId, process.order.items, process.order.totalCost,
                true: Boolean)
              OrderProcessed(process.orderId, order)
            } else if (succeedResponses.length + failedResponses.length == process.expectedResponses) {
                if (succeedResponses.nonEmpty) {
                  RollBackStockProcess(process.orderId, process.order, succeedResponses.length, 0, process.items, process.client)
                } else {
                  RollBackPaymentProcess(process.orderId, process.order, process.items, process.client)
                }
            } else {
              StockProcess(process.orderId, process.order, process.expectedResponses, succeedResponses, failedResponses,
                process.items, process.client)
            }
          case CancelPaymentProcessed(client) =>
            RollBackPaymentProcess(process.orderId, process.order, process.items, client)
          case _ => process
        }

      case process: RollBackStockProcess =>
        event match {
          case RollBackStockProcessed(receivedResponses) => receivedResponses match {
            case process.expectedResponses => RollBackPaymentProcess(process.orderId, process.order, process.items, process.client)
            case _ => RollBackStockProcess(process.orderId, process.order, process.expectedResponses, receivedResponses, process.items, process.client)
          }
          case _ => process
        }

      case process: RollBackPaymentProcess =>
        event match {
          case RollBackPaymentProcessed =>
            val order = Order(process.orderId, process.order.userId, process.order.items, process.order.totalCost, false: Boolean)
            OrderProcess(process.orderId, order, process.items, process.client)
          case _ => process
        }

      case processed: OrderProcessed =>
        event match {
          case RemoveOrderRequestReceived => Empty
          case _ => processed
        }

    }

    EventSourcedBehavior[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = Empty,
      commandHandler = commandHandler,
      eventHandler = eventHandler).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 3, keepNSnapshots = 3))

  }
  // public protocol

  sealed trait Command extends CborSerializable {
    def orderId: OrderId
  }

  final case class CreateOrderRequest(orderId: OrderId, userId: String, replyTo: ActorRef[Response]) extends Command
  final case class CheckoutOrderRequest(orderId: OrderId, replyTo: ActorRef[Response]) extends Command
  final case class RemoveOrderRequest(orderId: OrderId, replyTo: ActorRef[Response]) extends Command
  final case class FindOrderRequest(orderId: OrderId, replyTo: ActorRef[Response]) extends Command
  final case class AddItemToOrderRequest(orderId: OrderId, itemId: String, replyTo: ActorRef[Response]) extends Command
  final case class RemoveItemFromOrderRequest(orderId: OrderId, itemId: String, replyTo: ActorRef[Response]) extends Command
  final case class GetPaymentStatus(orderId: OrderId, replyTo: ActorRef[Response]) extends Command
  final case class CancelPayment(orderId: OrderId, userId: String, sender: ActorRef[Response]) extends Command

  final case object GracefulStop extends Command {
    // this message is intended to be sent directly from the parent shard, hence the orderId is irrelevant
    override def orderId: OrderId = OrderId("")
  }

  case class OrderId(id: String) extends AnyVal
  case class Order(orderId: OrderId, userId: String, var items: List[String], var totalCost: Long, var paid: Boolean)
  case class Status(paid: Boolean)

  sealed trait Event extends CborSerializable
  final case class CreateOrderRequestReceived(orderId: OrderId, userId: String, replyTo: ActorRef[Response]) extends Event
  final case class CheckOutOrderRequestReceived(replyTo: ActorRef[Response]) extends Event
  final case object RemoveOrderRequestReceived extends Event
  final case class AddItemToOrderRequestReceived(itemId: String, replyTo: ActorRef[Response]) extends Event
  final case class ItemAddedToOrder(itemId: String, price: Long) extends Event
  final case class ItemRemovedFromOrder(itemId: String) extends Event
  final case class PaymentProcessed(expectedResponses: Int) extends Event
  final case class StockProcessed(succeedResponses: List[String], failedResponses: List[String]) extends Event
  final case class RollBackStockProcessed(receivedResponses: Int) extends Event
  final case object RollBackPaymentProcessed extends Event
  final case class CancelPaymentProcessed(replyTo: ActorRef[Response]) extends Event
  final case object BackToOrderProcess extends Event


  sealed trait State
  final case object Empty extends State
  final case class OrderProcess(orderId: OrderId, order: Order, items: Map[String, Long],
                                client: ActorRef[Response]) extends State
  final case class PaymentProcess(orderId: OrderId, order: Order, items: Map[String, Long],
                                  client: ActorRef[Response]) extends State
  final case class StockProcess(orderId: OrderId, order: Order, expectedResponses: Int,
                                succeedResponses: List[String], failedResponses: List[String],
                                items: Map[String, Long], client: ActorRef[Response]) extends State
  final case class RollBackStockProcess(orderId: OrderId, order: Order, expectedResponses: Int, receivedResponses: Int,
                                        items: Map[String, Long], client: ActorRef[Response]) extends State
  final case class RollBackPaymentProcess(orderId: OrderId, order: Order, items: Map[String, Long],
                                          client: ActorRef[Response]) extends State
  final case class OrderProcessed(orderId: OrderId, order: Order) extends State


  sealed trait Response extends CborSerializable
  final case class OrderCreatedResponse(orderId: OrderId) extends Response
  final case class FindOrderResponse(order: Order) extends Response
  case object Succeed extends Response
  final case class Failed(reason: String) extends Response
  final case class PaymentStatus(paid: Status) extends Response

  // internal protocol
  sealed trait InternalMessage extends Command
  private final case class AdaptedPaymentResponse(orderId: OrderId, response: UserActor.UserResponse)
    extends InternalMessage
  private final case class AdaptedStockResponse(orderId: OrderId, response: StockActor.StockResponse)
    extends InternalMessage
  private final case class RequestedStockResponse(orderId: OrderId, response: StockActor.StockResponse)
    extends InternalMessage

}
