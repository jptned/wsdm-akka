package webshop.order

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import webshop.JsonFormats
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webshop.stock.Stock.ItemIdentifier
import webshop.user.UserRepository._
import akka.pattern.ask

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class OrderRoutes(orderRepository: ActorRef)(implicit val system: ActorSystem) extends JsonFormats  {

  import OrderHandler._

  private implicit lazy val internalTimeout = Timeout(100.milliseconds)
  implicit val scheduler = system.scheduler

  lazy val orderRoutes: Route =
    pathPrefix("orders") {
      concat(
        path("create" / Segment) { userId =>
          get {
            val identifierResponse: Future[GetOrderIdentifierResponse] = (orderRepository ? OrderHandler.CreateOrder(
              UserIdentifier(UUID.fromString(userId)))).mapTo[GetOrderIdentifierResponse]
            rejectEmptyResponse {
              onSuccess(identifierResponse) {
                case GetOrderIdentifierResponse(identifier) =>
                  complete(identifier)
              }
            }
          }
        },
        path("find" / Segment) { orderId =>
          get {
            println("orderId " + orderId)
            val maybeOrder = (orderRepository ? OrderHandler.FindOrder(OrderIdentifier(UUID.fromString(orderId)))).mapTo[GetOrderResponse]
            rejectEmptyResponse {
              onSuccess(maybeOrder) { response =>
                complete(response.maybeOrder)
              }
            }
          }
        },
        path("remove" / Segment) { orderId =>
          get {
            println("orderId " + orderId)
            val operationPerformed = (orderRepository ? RemoveOrder(OrderIdentifier(UUID.fromString(orderId)))).mapTo[OrderResponse]
            println("operationPerformed " + operationPerformed)
            onSuccess(operationPerformed) {
              case OrderHandler.Succeed => complete(StatusCodes.OK)
              case OrderHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("addItem" / Segment / Segment) { (orderId, itemId) =>
          post {
            val operationPerformed = (orderRepository ? AddItemToOrder(OrderIdentifier(UUID.fromString(orderId)),
              ItemIdentifier(UUID.fromString(itemId)))).mapTo[OrderResponse]
            onSuccess(operationPerformed) {
              case OrderHandler.Succeed => complete(StatusCodes.OK)
              case OrderHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        path("removeItem" / Segment / Segment) { (orderId, itemId) =>
          delete {
            val operationPerformed = (orderRepository ? RemoveItemFromOrder(OrderIdentifier(UUID.fromString(orderId)),
              ItemIdentifier(UUID.fromString(itemId)))).mapTo[OrderResponse]
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