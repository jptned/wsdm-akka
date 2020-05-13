package actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Stock {

  final case class Stock(item_id: Long, stock: Long, price: Long)

  final case class FindStock(item_id: Long, replyTo: ActorRef[Stock])

  final case class SubtractStock(item_id: Long, number: Long, replyTo: ActorRef[Stock])

  final case class AddStock(item_id: Long, number: Long, replyTo: ActorRef[Stock])

  final case class CreateStock(price: Long, replyTo: ActorRef[Stock])

  def apply(): Behavior[Stock] = {
    process(Map.empty[Int, Stock])
  }

  private def process(list: Map[Int, Stock]): Behavior[Stock] =
    Behaviors.receiveMessage {
      case FindStock(item_id, stock, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        Behaviors.same
      case SubtractStock(item_id, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        process(list)
      case AddStock(item_id, number, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        process(list)
      case CreateStock(item_id, replyTo) =>
        replyTo ! Stock(1, 1, 1)
        process(list)
    }
}
