package microservice.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}
import akka.cluster.ddata.{ReplicatedData, SelfUniqueAddress}
import microservice.types.{NotEnoughStockException, StockType, StockTypeKey}

import scala.concurrent.duration._

object StockActor {

  sealed trait Command
  final case class FindStock(replyTo: ActorRef[StockResponse]) extends Command
  final case class SubtractStock(number: Long, replyTo: ActorRef[StockResponse]) extends Command
  final case class AddStock(number: Long, replyTo: ActorRef[StockResponse]) extends Command
  final case class CreateStock(price: Long, replyTo: ActorRef[StockResponse]) extends Command

  sealed trait StockResponse
  final case class Stock(item_id: String, stock: BigInt, price: Long) extends StockResponse
  final case class Failed(reason: String, item_id : String) extends StockResponse
  final case class NotEnoughStock(item_id: String) extends StockResponse
  final case class Successful(item_id: String) extends StockResponse

  private sealed trait InternalCommand extends Command
  private case class InternalFindResponse(replyTo: ActorRef[StockResponse], rsp: GetResponse[StockType]) extends InternalCommand
  private case class InternalUpdateResponse[A <: ReplicatedData](replyTo: ActorRef[StockResponse], rsp: UpdateResponse[A]) extends InternalCommand
  private case class InternalCreateResponse(replyTo: ActorRef[StockResponse], getResponse: GetResponse[StockType]) extends InternalCommand

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  def apply(item_id: String): Behavior[Command] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[Command, StockType] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      val DataKey = StockTypeKey("stock-" + item_id)

      def behavior = Behaviors.receiveMessagePartial(
        receiveFindStock
          .orElse(receiveSubtractStock)
          .orElse(receiveAddStock)
          .orElse(receiveCreateStock)
          .orElse(receiveOther)
      )

      def receiveFindStock: PartialFunction[Command, Behavior[Command]] = {
        case FindStock(replyTo) =>
          replicator.askGet(
            askReplyTo => Get(DataKey, readMajority, askReplyTo),
            rsp => InternalFindResponse(replyTo, rsp))

          Behaviors.same

        case InternalFindResponse(replyTo, g@GetSuccess(DataKey, _)) =>
          val stock = g.get(DataKey)
          replyTo ! Stock(stock.item_id, stock.stockValue, stock.price)
          Behaviors.stopped

        case InternalFindResponse(replyTo, NotFound(DataKey, _)) =>
          replyTo ! Failed("Couldn't find " + DataKey, item_id)
          Behaviors.stopped

        case InternalFindResponse(replyTo, e) =>
          replyTo ! Failed("Couldn't find " + e, item_id)
          Behaviors.stopped

        case InternalFindResponse(replyTo, GetFailure(DataKey, _)) =>
          // ReadMajority failure, try again with local read
          replicator.askGet(
            askReplyTo => Get(DataKey, ReadLocal, askReplyTo),
            rsp => InternalFindResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveSubtractStock: PartialFunction[Command, Behavior[Command]] = {
        case SubtractStock(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, writeMajority, askReplyTo) {
              case Some(stock) => stock.decrement(number)
              case None => throw new Exception("Stock does not exist")
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveAddStock: PartialFunction[Command, Behavior[Command]] = {
        case AddStock(number, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, writeMajority, askReplyTo) {
              case Some(stock) => stock.increment(number)
              case None => throw new Exception("Stock does not exist")
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveCreateStock: PartialFunction[Command, Behavior[Command]] = {
        case CreateStock(price, replyTo) =>
          replicator.askUpdate(
            askReplyTo => Update(DataKey, writeMajority, askReplyTo) {
              case Some(_) => throw new Exception("Stock already exists")
              case None => StockType.create(item_id, price)
            },
            rsp => InternalUpdateResponse(replyTo, rsp))

          Behaviors.same
      }

      def receiveOther: PartialFunction[Command, Behavior[Command]] = {
        case InternalUpdateResponse(replyTo, _: UpdateSuccess[_]) =>
          replyTo ! Successful(item_id)
          Behaviors.stopped
        case InternalUpdateResponse(replyTo, _: UpdateTimeout[_]) =>
          // UpdateTimeout, will eventually be replicated
          replyTo ! Successful(item_id)
          Behaviors.stopped
        case InternalUpdateResponse(replyTo, e: UpdateFailure[a]) =>
          e match {
            case ModifyFailure(_, _, NotEnoughStockException(_, _), _) =>
              replyTo ! NotEnoughStock(item_id)
            case _ =>
              replyTo ! Failed("Failure updating " + e, item_id)
          }

          Behaviors.stopped
      }

      behavior
    }
  }

}
