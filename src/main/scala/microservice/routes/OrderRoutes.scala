package microservice.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.AskPattern.Askable
import microservice.JsonFormats
import microservice.actors.{OrderManager, OrderActor}
import microservice.actors.OrderActor.{FindOrderResponse, OrderCreatedResponse, OrderId, Response}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class OrderRoutes(orderManager: ActorRef[OrderManager.ManagerCommand])(implicit val system: ActorSystem[_]) extends JsonFormats  {

  private implicit lazy val internalTimeout = Timeout(5000.seconds)
  implicit val scheduler = system.scheduler

  lazy val orderRoutes: Route =
    pathPrefix("orders") {
      concat(
        path("create" / JavaUUID) { userId =>
          post {
            val identifierResponse: Future[Response] = orderManager.ask(OrderManager.CreateOrder(userId.toString, _))
            rejectEmptyResponse {
              onSuccess(identifierResponse) {
                case OrderCreatedResponse(orderId) => complete(orderId)
                case _ => complete(StatusCodes.BadRequest)
              }
            }
          }
        },
        path("find" / JavaUUID) { orderId =>
          get {
            val maybeOrder: Future[Response] = orderManager.ask(OrderManager.FindOrder(OrderId(orderId.toString), _))
            rejectEmptyResponse {
              onSuccess(maybeOrder) {
                case FindOrderResponse(order) => complete(order)
                case OrderActor.Failed(reason) => complete(StatusCodes.BadRequest -> reason)
                case _ => complete(StatusCodes.BadRequest)
              }
            }
          }
        },
        path("remove" / JavaUUID) { orderId =>
          delete {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.RemoveOrder(OrderId(orderId.toString), _))
            onSuccess(operationPerformed) {
              case OrderActor.Succeed => complete(StatusCodes.OK)
              case OrderActor.Failed(reason) => complete(StatusCodes.BadRequest -> reason)
              case _ => complete(StatusCodes.BadRequest)
            }
          }
        },
        path("addItem" / JavaUUID / JavaUUID) { (orderId, itemId) =>
          post {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.AddItemToOrder(OrderId(orderId.toString), itemId.toString, _))
            onSuccess(operationPerformed) {
              case OrderActor.Succeed => complete(StatusCodes.OK)
              case OrderActor.Failed(reason) => complete(StatusCodes.BadRequest -> reason)
              case _ => complete(StatusCodes.BadRequest)
            }
          }
        },
        path("removeItem" / JavaUUID / JavaUUID) { (orderId, itemId) =>
          delete {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.RemoveItemFromOrder(OrderId(orderId.toString),
              itemId.toString, _))
            onSuccess(operationPerformed) {
              case OrderActor.Succeed => complete(StatusCodes.OK)
              case OrderActor.Failed(reason) => complete(StatusCodes.BadRequest -> reason)
              case _ => complete(StatusCodes.BadRequest)
            }
          }
        },
        path("checkout" / JavaUUID) { orderId =>
          post {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.CheckoutOrder(OrderId(orderId.toString), _))
            rejectEmptyResponse {
              onSuccess(operationPerformed) {
                case OrderActor.Succeed => complete(StatusCodes.OK)
                case OrderActor.Failed(reason) => complete(StatusCodes.BadRequest -> reason)
                case _ => complete(StatusCodes.BadRequest)
              }
            }
          }
        }
      )
    }
}
