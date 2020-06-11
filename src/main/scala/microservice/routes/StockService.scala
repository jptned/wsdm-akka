package microservice.routes

import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, concat, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import microservice.Webserver.Message
import microservice.actors.Stock
import microservice.actors.Stock.StockResponse
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._

class StockService(implicit system: ActorSystem[_], implicit val ct: ActorContext[Message]) {
  implicit val timeout: Timeout = Timeout(5000.millis)

  val stockRoutes: Route =
    pathPrefix("stock") {
      concat(
        path("find" / JavaUUID) { itemId  =>
          get {
            val actor = ct.spawn(Stock(itemId.toString), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(Stock.FindStock)
            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Stock(item_id: String, stock: BigInt, price: Long) =>
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
            val actor = ct.spawn(Stock(itemId.toString), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(Stock.SubtractStock(number, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Successful(_) =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> true).toString()))
                case Stock.NotEnoughStock(_) =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> false).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.BadRequest)
              }
            }
          }
        },
        path("add" / JavaUUID / LongNumber) { (itemId, number) =>
          post {
            val actor = ct.spawn(Stock(itemId.toString), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(Stock.AddStock(number, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Successful(_) =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> true).toString()))
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

            val actor = ct.spawn(Stock(itemId), "stock-"+itemId)
            val res: Future[StockResponse] = actor.ask(Stock.CreateStock(price, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Successful(_) =>
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
