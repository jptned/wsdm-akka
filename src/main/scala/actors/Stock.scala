package actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Stock {
  sealed trait StockCommand
  sealed trait StockQuery extends StockCommand

  // responses
  final case class Stock(item_id: Long, stock: Long, price: Long)

  // queries
  final case class FindStock(item_id: Long, replyTo: ActorRef[Stock]) extends StockQuery
  final case class SubtractStock(item_id: Long, number: Long, replyTo: ActorRef[Stock]) extends StockQuery
  final case class AddStock(item_id: Long, number: Long, replyTo: ActorRef[Stock]) extends StockQuery
  final case class CreateStock(price: Long, replyTo: ActorRef[Stock]) extends StockQuery

  // state
  final case class StockState(list: Map[Int, Stock])

  def apply(): Behavior[StockQuery] = {
    process(StockState(Map.empty[Int, Stock]))
  }

  def process(state: StockState): Behavior[StockQuery] =
    Behaviors.receiveMessage {
      case FindStock(item_id, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        Behaviors.same
      case SubtractStock(item_id, number, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        Behaviors.same
      case AddStock(item_id, number, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        Behaviors.same
      case CreateStock(item_id, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        Behaviors.same
    }
}
