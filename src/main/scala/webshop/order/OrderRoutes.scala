package webshop.order

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import webshop.JsonFormats
import webshop.user.UserRepository
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webshop.stock.Stock.ItemIdentifier
import webshop.user.UserRepository._

import scala.concurrent.Future
import scala.concurrent.duration._


class OrderRoutes(orderRepository: ActorRef[OrderHandler.OrderCommand])(implicit val system: ActorSystem[_]) extends JsonFormats {

//trait OrderRoute extends OrderHandler with JsonFormats {
  import OrderHandler._
  import akka.actor.typed.scaladsl.AskPattern.Askable

//  implicit val system: ActorSystem[_]
  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler

  val orderRoutes: Route = {
    pathPrefix("orders") {
      concat(
        path("create" / Segment) { userId =>
          post {
            val identifierResponse: Future[GetOrderIdentifierResponse] = orderRepository.ask(CreateOrder(UserIdentifier(UUID
              .fromString(userId)), _))
            rejectEmptyResponse {
              onSuccess(identifierResponse) { response =>
                complete(response.identifier)
              }
            }
          }
        },
        path("find" / Segment) { orderId =>
          get {
            val maybeOrder: Future[GetOrderResponse] = orderRepository.ask(FindOrder(OrderIdentifier(UUID.fromString(orderId)), _))
            rejectEmptyResponse {
              onSuccess(maybeOrder) { response =>
                complete(response.maybeOrder)
              }
            }
          }
        },
        path("remove" / Segment) { orderId =>
          delete {
            val operationPerformed: Future[OrderResponse] = orderRepository.ask(RemoveOrder(OrderIdentifier(UUID.fromString(orderId)), _))
            onSuccess(operationPerformed) {
              case OrderHandler.Succeed => complete(StatusCodes.OK)
              case OrderHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("addItem" / Segment / Segment) { (orderId, itemId) =>
          post {
            val operationPerformed: Future[OrderResponse] = orderRepository.ask(AddItemToOrder(
              OrderIdentifier(UUID.fromString(orderId)), ItemIdentifier(UUID.fromString(itemId)), _))
            onSuccess(operationPerformed) { _ =>
              complete(StatusCodes.OK)
            }
          }
        },
        path("removeItem" / Segment / Segment) { (orderId, itemId) =>
          delete {
            val operationPerformed: Future[OrderResponse] = orderRepository.ask(RemoveItemFromOrder(
              OrderIdentifier(UUID.fromString(orderId)), ItemIdentifier(UUID.fromString(itemId)), _))
            onSuccess(operationPerformed) {
              case OrderHandler.Succeed => complete(StatusCodes.OK)
              case OrderHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
//        path("checkout" / Segment) { orderId =>
//          post {
//            val identifierResponse: Future[GetUserIdentifierResponse] = orderRepository.ask(UserRepository.CreateUser)
//            rejectEmptyResponse {
//              onSuccess(identifierResponse) { response =>
//                complete(response.identifier)
//              }
//            }
//          }
//        }
      )
    }
  }


}
