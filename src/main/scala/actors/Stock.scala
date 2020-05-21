package actors

import scala.concurrent.duration._
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.{ORMap, ORMapKey, PNCounterKey, ReplicatedData, SelfUniqueAddress}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}

object Stock {
  sealed trait Command
  final case class FindStock(replyTo: ActorRef[StockResponse]) extends Command
  final case class SubtractStock(item_id: Long, number: Long, replyTo: ActorRef[StockResponse]) extends Command
  final case class AddStock(item_id: Long, number: Long, replyTo: ActorRef[StockResponse]) extends Command
  final case class CreateStock(price: Long, replyTo: ActorRef[Stock]) extends Command

  final case class Stock(item_id: Long, stock: Long, price: Long)

  private sealed trait InternalCommand extends Command
  private case class InternalFindResponse(replyTo: ActorRef[Stock], rsp: GetResponse[ORMap[String, Stock]]) extends InternalCommand
  private case class InternalSubtractResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalAddResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalCreateResponse(stock_id: Long, getResponse: GetResponse[ORMap[String, Stock]]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  def apply(item_id: Long): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, ORMap[String, Stock]] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      val QuantityDataKey = PNCounterKey("stock-" + userId)

      def behavior = Behaviors.receiveMessagePartial(
        receiveFindStock
          .orElse(reveiveSubtractStock)
          .orElse(receiveAddStock)
          .orElse(receiveCreateStock)
      )

      def receiveFindStock: PartialFunction[Command, Behavior[Command]] = {
        case FindStock(replyTo) =>
          replicator.askGet(
            askReplyTo => Get(DataKey, readMajority, askReplyTo),
            rsp => InternalFindResponse(replyTo, rsp))

          Behaviors.same

        case InternalFindResponse(replyTo, g @ GetSuccess(DataKey, _)) =>
          val data = g.get(DataKey)
          val stock = data.entries.values
          replyTo ! stock.toList.head
          Behaviors.same

        case InternalFindResponse(replyTo, NotFound(DataKey, _)) =>
          // Todo: send wrong response
          Behaviors.same

        case InternalFindResponse(replyTo, GetFailure(DataKey, _)) =>
          // ReadMajority failure, try again with local read
          replicator.askGet(
            askReplyTo => Get(DataKey, ReadLocal, askReplyTo),
            rsp => InternalFindResponse(replyTo, rsp))

          Behaviors.same
      }

      def reveiveSubtractStock: PartialFunction[Command, Behavior[Command]] = {}
      def receiveAddStock: PartialFunction[Command, Behavior[Command]] = {}
      def receiveCreateStock: PartialFunction[Command, Behavior[Command]] = {}

      def receiveAddItem: PartialFunction[Command, Behavior[Command]] = {
        case AddItem(item) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, ORMap.empty[String, LineItem], writeMajority, askReplyTo) {
              cart => updateCart(cart, item)
            },
            InternalUpdateResponse.apply)

          Behaviors.same
      }

      def updateCart(data: ORMap[String, LineItem], item: LineItem): ORMap[String, LineItem] = {
        data.get(item.productId) match {
          case Some(LineItem(_, _, existingQuantity)) =>
            data :+ (item.productId -> item.copy(quantity = existingQuantity + item.quantity))
          case None => data :+ (item.productId -> item)
        }
      }

      def receiveRemoveItem: PartialFunction[Command, Behavior[Command]] = {
        case RemoveItem(productId) =>
          // Try to fetch latest from a majority of nodes first, since ORMap
          // remove must have seen the item to be able to remove it.
          replicator.askGet(
            askReplyTo => Get(DataKey, readMajority, askReplyTo),
            rsp => InternalRemoveItem(productId, rsp))

          Behaviors.same

        case InternalRemoveItem(productId, GetSuccess(DataKey, _)) =>
          removeItem(productId)
          Behaviors.same

        case InternalRemoveItem(productId, GetFailure(DataKey, _)) =>
          // ReadMajority failed, fall back to best effort local value
          removeItem(productId)
          Behaviors.same

        case InternalRemoveItem(_, NotFound(DataKey, _)) =>
          // nothing to remove
          Behaviors.same
      }

      def removeItem(productId: String): Unit = {
        replicator.askUpdate(
          askReplyTo => Update(DataKey, ORMap.empty[String, LineItem], writeMajority, askReplyTo) {
            _.remove(node, productId)
          },
          InternalUpdateResponse.apply)
      }

      def receiveOther: PartialFunction[Command, Behavior[Command]] = {
        case InternalUpdateResponse(_: UpdateSuccess[_]) => Behaviors.same
        case InternalUpdateResponse(_: UpdateTimeout[_]) => Behaviors.same
        // UpdateTimeout, will eventually be replicated
        case InternalUpdateResponse(e: UpdateFailure[_]) => throw new IllegalStateException("Unexpected failure: " + e)
      }

      behavior
    }
  }

}
