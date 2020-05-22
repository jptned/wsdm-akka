package actors

import scala.concurrent.duration._
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.{LWWMap, LWWMapKey, ReplicatedData, SelfUniqueAddress}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}
import types.{StockType, StockTypeKey}

object Stock {
  sealed trait Command
  final case class FindStock(replyTo: ActorRef[StockResponse]) extends Command
  final case class SubtractStock(number: Long, replyTo: ActorRef[StockResponse]) extends Command
  final case class AddStock(number: Long, replyTo: ActorRef[StockResponse]) extends Command
  final case class CreateStock(price: Long, replyTo: ActorRef[StockResponse]) extends Command

  sealed trait StockResponse
  final case class Stock(item_id: String, stock: BigInt, price: Long) extends StockResponse
  final case class Failed(reason: String) extends StockResponse

  private sealed trait InternalCommand extends Command
  private case class InternalFindResponse(replyTo: ActorRef[StockResponse], rsp: GetResponse[StockType]) extends InternalCommand
  private case class InternalUpdateResponse[A <: ReplicatedData](rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalCreateResponse(stock_id: Long, getResponse: GetResponse[StockType]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  def apply(item_id: String): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, StockType] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      val DataKey = StockTypeKey("stock-" + item_id)

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
          val stock = g.get(DataKey)
          replyTo ! Stock(stock.item_id, stock.stockValue, stock.price)
          Behaviors.same

        case InternalFindResponse(replyTo, NotFound(DataKey, _)) =>
          replyTo ! Failed("Couldn't find " + DataKey)
          Behaviors.same

        case InternalFindResponse(replyTo, GetFailure(DataKey, _)) =>
          // ReadMajority failure, try again with local read
          replicator.askGet(
            askReplyTo => Get(DataKey, ReadLocal, askReplyTo),
            rsp => InternalFindResponse(replyTo, rsp))

          Behaviors.same
      }

      def reveiveSubtractStock: PartialFunction[Command, Behavior[Command]] = {
        case SubtractStock(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, StockType.create(item_id, 0), writeMajority, askReplyTo) {
              stock => stock.decrement(number)
            },
            InternalUpdateResponse.apply)

          Behaviors.same
      }

      def receiveAddStock: PartialFunction[Command, Behavior[Command]] = {
        case AddStock(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, StockType.create(item_id, 0), writeMajority, askReplyTo) {
              stock => stock.increment(number)
            },
            InternalUpdateResponse.apply)

          Behaviors.same}

      def receiveCreateStock: PartialFunction[Command, Behavior[Command]] = {}

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
