package microservice.routes

import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, concat, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import microservice.actors.StockActor
import microservice.actors.StockActor.StockResponse
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._

class StockService(implicit system: ActorSystem[_], implicit val ct: ActorContext[Nothing]) {
  implicit val timeout: Timeout = Timeout(5000.millis)

  val stockRoutes: Route =
    pathPrefix("stock") {
      concat(
        path("find" / JavaUUID) { itemId  =>
          get {
            val actor = ct.spawn(StockActor(itemId.toString), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(StockActor.FindStock)
            rejectEmptyResponse {
              onSuccess(res) {
                case StockActor.Stock(item_id: String, stock: BigInt, price: Long) =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("stock" -> stock, "price" -> price).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
              }
            }
          }
        },
        path("subtract" / JavaUUID / LongNumber) { (itemId, number) =>
          post {
            val actor = ct.spawn(StockActor(itemId.toString), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(StockActor.SubtractStock(number, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case StockActor.Successful(_) =>
                  ct.stop(actor)
                  complete(StatusCodes.OK)
                case StockActor.NotEnoughStock(_) =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
              }
            }
          }
        },
        path("add" / JavaUUID / LongNumber) { (itemId, number) =>
          post {
            val actor = ct.spawn(StockActor(itemId.toString), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(StockActor.AddStock(number, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case StockActor.Successful(_) =>
                  ct.stop(actor)
                  complete(StatusCodes.OK)
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
              }
            }
          }
        },
        path("item" / "create" / LongNumber) { price =>
          post {
            val itemId = UUID.randomUUID().toString

            val actor = ct.spawn(StockActor(itemId), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(StockActor.CreateStock(price, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case StockActor.Successful(_) =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("item_id" -> itemId).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
              }
            }
          }
        },
      )
    }
}
