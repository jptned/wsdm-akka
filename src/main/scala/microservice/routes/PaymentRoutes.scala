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


class PaymentRoutes(orderManager: ActorRef[OrderManager.ManagerCommand])(implicit val system: ActorSystem[_]) extends JsonFormats  {

  private implicit lazy val internalTimeout = Timeout(5000.milliseconds)
  implicit val scheduler = system.scheduler

  lazy val paymentRoutes: Route =
    pathPrefix("payment") {
      concat(
        path("pay" / JavaUUID / JavaUUID) { (userId, orderId) =>
          post {
            complete(StatusCodes.BadRequest)
          }
        },
        path("cancel" / JavaUUID / JavaUUID) { (userId, orderId) =>
          post {
            complete(StatusCodes.BadRequest)
          }
        },
        path("status" / JavaUUID) { orderId =>
          get {
            val operationPerformed: Future[Response] = orderManager.ask(OrderManager.GetPaymentStatus(OrderId(orderId.toString), _))
            onSuccess(operationPerformed) {
              case OrderActor.PaymentStatus(status) => complete(status)
              case _ => complete(StatusCodes.BadRequest)
            }
          }
        }
      )
    }
}
