package microservice

import java.util.UUID

import actors.Stock
import actors.Stock.StockResponse
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, concat, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import microservice.Webserver.Message
import akka.actor.typed.scaladsl.AskPattern._
import play.api.libs.json.Json

import scala.concurrent.duration._

class Stockservice(implicit system: ActorSystem[_], implicit val ct: ActorContext[Message]) {
  implicit val timeout: Timeout = Timeout(5000.millis)

  val stockRoutes: Route =
    pathPrefix("stock") {
      concat(
        path("find" / LongNumber) { itemId =>
          get {
            val actor = ct.spawn(Stock(itemId.toString), "stock-"+itemId)
            val res = actor.ask(Stock.FindStock)
            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Stock(item_id: String, stock: BigInt, price: Long) =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("stock" -> stock, "price" -> price).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("subtract" / LongNumber / LongNumber) { (itemId, number) =>
          post {
            val success = true
            val actor = ct.spawn(Stock(itemId.toString), "stock-"+itemId)
            val res = actor.ask(Stock.SubtractStock(number, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Successful() =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> true).toString()))
                case Stock.NotEnoughStock() =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> false).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.InternalServerError)
              }
            }
            complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> success).toString()))
          }
        },
        path("add" / LongNumber / LongNumber) { (itemId, number) =>
          post {
            val actor = ct.spawn(Stock(itemId.toString), "stock-"+itemId)
            val res = actor.ask(Stock.AddStock(number, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Successful() =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("success" -> true).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
        path("item" / "create" / LongNumber) { price =>
          post {
            val itemId = UUID.randomUUID().toString

            val actor = ct.spawn(Stock(itemId.toString), "stock-"+itemId)
            val res = actor.ask(Stock.CreateStock(price, _))

            rejectEmptyResponse {
              onSuccess(res) {
                case Stock.Successful() =>
                  ct.stop(actor)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.obj("item_id" -> itemId).toString()))
                case _ =>
                  ct.stop(actor)
                  complete(StatusCodes.InternalServerError)
              }
            }
          }
        },
      )
    }
}
