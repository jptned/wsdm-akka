package microservice.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.AskPattern.Askable
import microservice.JsonFormats
import microservice.actors.{OrderManager, OrderRequest}
import microservice.actors.OrderRequest.{FindOrderResponse, OrderCreatedResponse, OrderId, Response}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class OrderRoutes(orderManager: ActorRef[OrderManager.ManagerCommand])(implicit val system: ActorSystem[_]) extends JsonFormats  {

  private implicit lazy val internalTimeout = Timeout(100.milliseconds)
  implicit val scheduler = system.scheduler

  lazy val orderRoutes: Route =
    pathPrefix("orders") {
      concat(
        path("create" / Segment) { userId =>
          post {
            val identifierResponse: Future[OrderCreatedResponse] = orderManager.ask(OrderManager.CreateOrder(userId, _))
            rejectEmptyResponse {
              onSuccess(identifierResponse) { response =>
                complete(response.orderId)
              }
            }
          }
        },
        path("find" / Segment) { orderId =>
          get {
            val maybeOrder: Future[FindOrderResponse] = orderManager.ask(OrderManager.FindOrder(OrderId(orderId), _))
            rejectEmptyResponse {
              onSuccess(maybeOrder) { response =>
                complete(response.order)
              }
            }
          }
        },
        path("remove" / Segment) { orderId =>
          delete {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.RemoveOrder(OrderId(orderId), _))
            onSuccess(operationPerformed) {
              case OrderRequest.Succeed => complete(StatusCodes.OK)
              case OrderRequest.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("addItem" / Segment / Segment) { (orderId, itemId) =>
          post {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.AddItemToOrder(OrderId(orderId), itemId, _))
            onSuccess(operationPerformed) {
              case OrderRequest.Succeed => complete(StatusCodes.OK)
              case OrderRequest.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("removeItem" / Segment / Segment) { (orderId, itemId) =>
          delete {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.RemoveItemFromOrder(OrderId(orderId),
              itemId, _))
            onSuccess(operationPerformed) {
              case OrderRequest.Succeed => complete(StatusCodes.OK)
              case OrderRequest.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("checkout" / Segment) { orderId =>
          post {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.CheckoutOrder(OrderId(orderId), _))
            rejectEmptyResponse {
              onSuccess(operationPerformed) {
                case OrderRequest.Succeed => complete(StatusCodes.OK)
                case OrderRequest.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
              }
            }
          }
        }
      )
    }
}
