package microservice.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.AskPattern.Askable
import microservice.models.Inventory.InventoryResponse
import microservice.models.{Inventory, InventoryManager}
import microservice.setups.Initials.ItemId
import microservice.setups.JsonFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class InventoryRoutes(inventoryManager: ActorRef[InventoryManager.InventoryManagerCommand])(implicit val system: ActorSystem[_]) extends JsonFormats {

  private implicit lazy val internalTimeout = Timeout(100.milliseconds)
  implicit val scheduler = system.scheduler

  lazy val inventoryRoutes: Route =
    pathPrefix("stock") {
      concat(
        path("create" / LongNumber) { price =>
          post {
            val identifierResponse: Future[InventoryResponse] = inventoryManager.ask(InventoryManager.CreateItem(price, _))
            rejectEmptyResponse {
              onSuccess(identifierResponse) {
                case Inventory.Created(id) => complete(id)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("find" / Segment) { itemId =>
          get {
            val maybeUser: Future[InventoryResponse] = inventoryManager.ask(InventoryManager.FindItem(ItemId(itemId), _))
            rejectEmptyResponse {
              onSuccess(maybeUser) {
                case Inventory.ItemFound(item) => complete(item)
                case Inventory.ItemNotFound(id) => complete(StatusCodes.InternalServerError -> id.id)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("subtract" / Segment / LongNumber) { (itemId, amount) =>
          post {
            val operationPerformed: Future[InventoryResponse] = inventoryManager.ask(InventoryManager.SubtractStock(ItemId(itemId),
              amount, _))
            onSuccess(operationPerformed) {
              case Inventory.ChangedStockSucceed => complete(StatusCodes.OK)
              case Inventory.ChangedStockFailed => complete(StatusCodes.InternalServerError)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        },
        path("add" / Segment / LongNumber) { (itemId, amount) =>
          delete {
            val operationPerformed: Future[InventoryResponse] = inventoryManager.ask(InventoryManager.AddStock(ItemId(itemId),
              amount, _))
            onSuccess(operationPerformed) {
              case Inventory.ChangedStockSucceed => complete(StatusCodes.OK)
              case Inventory.ChangedStockFailed => complete(StatusCodes.InternalServerError)
              case _ => complete(StatusCodes.InternalServerError)
            }
          }
        }
      )
    }
}
