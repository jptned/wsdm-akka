package webshop.order

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import webshop.JsonFormats
import webshop.user.UserRepository
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import webshop.stock.Stock.ItemIdentifier
import webshop.user.UserRepository._
import akka.pattern.ask
//import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class OrderRoutes(orderRepository: ActorRef)(implicit val system: ActorSystem) extends JsonFormats  {

  import OrderHandler._

//  implicit def system: ActorSystem
//  def orderRepository: ActorRef

  private implicit lazy val internalTimeout = Timeout(100.milliseconds)

  lazy val orderRoutes: Route =
    pathPrefix("orders") {
      concat(
        path("create" / Segment) { userId =>
          get {
            val identifierResponse: Future[GetOrderIdentifierResponse] = (orderRepository ? OrderHandler.CreateOrder(
              UserIdentifier(UUID.fromString(userId)))).mapTo[GetOrderIdentifierResponse]
            //              .mapTo[GetOrderIdentifierResponse]
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
//class OrderRoutes(orderRepository: ActorRef)(implicit ctx: ExecutionContext) extends JsonFormats {
//
////trait OrderRoute extends OrderHandler with JsonFormats {
//  import OrderHandler._
//  import akka.actor.typed.scaladsl.AskPattern.Askable
//
////  implicit val system: ActorSystem[_]
////  val clusterListener = system.actorOf(Props(new OrderHandler()))
//  implicit val timeout: Timeout = 30.seconds
////  implicit val scheduler = system.scheduler
//
//  val orderRoutes: Route = {
//    pathPrefix("orders") {
//      concat(
//        path("create" / Segment) { userId =>
//          get {
//            val identifierResponse = orderRepository ? OrderHandler.CreateOrder(UserIdentifier(UUID.fromString(userId)))
////              .mapTo[GetOrderIdentifierResponse]
//            rejectEmptyResponse {
//              onSuccess(identifierResponse) {
//                case GetOrderIdentifierResponse(identifier) =>
//                  complete(identifier)
//              }
//            }
//          }
//        },
//        path("find" / Segment) { orderId =>
//          get {
//            val maybeOrder = (orderRepository ? OrderHandler.FindOrder(OrderIdentifier(UUID.fromString(orderId)))).mapTo[GetOrderResponse]
//            rejectEmptyResponse {
//              onSuccess(maybeOrder) { response =>
//                complete(response.maybeOrder)
//              }
//            }
//          }
//        },
//        path("remove" / Segment) { orderId =>
//          delete {
//            val operationPerformed = (orderRepository ? RemoveOrder(OrderIdentifier(UUID.fromString(orderId)))).mapTo[OrderResponse]
//            onSuccess(operationPerformed) {
//              case OrderHandler.Succeed => complete(StatusCodes.OK)
//              case OrderHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
//            }
//          }
//        },
//        path("addItem" / Segment / Segment) { (orderId, itemId) =>
//          post {
//            val operationPerformed = (orderRepository ? AddItemToOrder(OrderIdentifier(UUID.fromString(orderId)),
//              ItemIdentifier(UUID.fromString(itemId)))).mapTo[OrderResponse]
//            onSuccess(operationPerformed) {
//              case OrderHandler.Succeed => complete(StatusCodes.OK)
//              case OrderHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
//            }
//          }
//        },
//        path("removeItem" / Segment / Segment) { (orderId, itemId) =>
//          delete {
//            val operationPerformed = (orderRepository ? RemoveItemFromOrder(OrderIdentifier(UUID.fromString(orderId)),
//              ItemIdentifier(UUID.fromString(itemId)))).mapTo[OrderResponse]
//            onSuccess(operationPerformed) {
//              case OrderHandler.Succeed => complete(StatusCodes.OK)
//              case OrderHandler.Failed(reason) => complete(StatusCodes.InternalServerError -> reason)
//            }
//          }
//        },
////        path("checkout" / Segment) { orderId =>
////          post {
////            val identifierResponse: Future[GetUserIdentifierResponse] = orderRepository.ask(UserRepository.CreateUser)
////            rejectEmptyResponse {
////              onSuccess(identifierResponse) { response =>
////                complete(response.identifier)
////              }
////            }
////          }
////        }
//      )
//    }
//  }
//
//
//}
