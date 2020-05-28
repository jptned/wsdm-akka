package webstore

import java.util.UUID

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern.Askable
import webstore.OrderRequestHandler.{FindOrderResponse, OrderCreatedResponse, Response}
import webstore.OrderStorage.OrderId
import webstore.UserStorage.UserId

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class OrderRoutes(orderHander: ActorRef[OrderHandler.HandlerCommand])(implicit val system: ActorSystem[_]) extends JsonFormats  {

  private implicit lazy val internalTimeout = Timeout(100.milliseconds)
  implicit val scheduler = system.scheduler

  lazy val orderRoutes: Route =
    pathPrefix("orders") {
      concat(
        path("create" / Segment) { userId =>
          post {
            val identifierResponse: Future[OrderCreatedResponse] = orderHander.ask(OrderHandler.CreateOrder(UserId(userId), _))
            rejectEmptyResponse {
              onSuccess(identifierResponse) { response =>
                complete(response.orderId)
              }
            }
          }
        },
        path("find" / Segment) { orderId =>
          get {
            val maybeOrder: Future[FindOrderResponse] = orderHander.ask(OrderHandler.FindOrder(OrderId(orderId), _))
            rejectEmptyResponse {
              onSuccess(maybeOrder) { response =>
                complete(response.order)
              }
            }
          }
        },
        path("remove" / Segment) { orderId =>
          delete {
            val operationPerformed: Future[Response] = orderHander.ask(OrderHandler.RemoveOrder(OrderId(orderId), _))
            onSuccess(operationPerformed) {
              case OrderRequestHandler.Succeed => complete(StatusCodes.OK)
              case OrderRequestHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("addItem" / Segment / Segment) { (orderId, itemId) =>
          post {
            val operationPerformed: Future[Response] = orderHander.ask(OrderHandler.AddItemToOrder(OrderId(orderId), itemId, _))
            onSuccess(operationPerformed) {
              case OrderRequestHandler.Succeed => complete(StatusCodes.OK)
              case OrderRequestHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("removeItem" / Segment / Segment) { (orderId, itemId) =>
          delete {
            val operationPerformed: Future[Response] = orderHander.ask(OrderHandler.RemoveItemFromOrder(OrderId(orderId), itemId, _))
            onSuccess(operationPerformed) {
              case OrderRequestHandler.Succeed => complete(StatusCodes.OK)
              case OrderRequestHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("checkout" / Segment) { orderId =>
          post {
            val operationPerformed: Future[Response] = orderHander.ask(OrderHandler.CheckoutOrder(OrderId(orderId), _))
            rejectEmptyResponse {
              onSuccess(operationPerformed) {
                case OrderRequestHandler.Succeed => complete(StatusCodes.OK)
                case OrderRequestHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
              }
            }
          }
        }
      )
    }
}