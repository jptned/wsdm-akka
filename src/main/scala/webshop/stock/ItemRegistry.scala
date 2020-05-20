package webshop.stock

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable

final case class Item(itemId: String, price: Int, stock: Int)
final case class Stock(items: immutable.Seq[Item])

object ItemRegistry {

  sealed trait Command
  final case class CreateItem(item: Item, replyTo: ActorRef[ActionPerformed]) extends Command
  final case class GetItem(id: String, replyTo: ActorRef[GetItemResponse]) extends Command
  final case class DeleteUser(id: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class GetItemResponse(maybeItem: Option[Item])
  final case class ActionPerformed(description: String)

  def apply(): Behavior[Command] = registry(Set.empty)

  private def registry(items: Set[Item]): Behavior[Command] = Behaviors.receiveMessage {
    case CreateItem(item, replyTo) =>
      replyTo ! ActionPerformed(s"Item /${item.itemId} created.")
      registry(items + item)
    case GetItem(id, replyTo) =>
      replyTo ! GetItemResponse(items.find(_.itemId == id))
      Behaviors.same
    case DeleteUser(id, replyTo) =>
      replyTo ! ActionPerformed(s"Item /$id deleted.")
      registry(items.filterNot(_.itemId == id))
  }

}