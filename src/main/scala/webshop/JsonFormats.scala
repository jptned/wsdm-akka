package webshop
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat, RootJsonWriter}
import webshop.order.OrderHandler.{Order, OrderIdentifier, StoredOrder}
import webshop.order.OrderStatus
import webshop.user.UserRepository.ActionPerformed


trait JsonFormats extends DefaultJsonProtocol with SprayJsonSupport {
  import DefaultJsonProtocol._
  import webshop.user.UserRepository._

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(uuid) => UUID.fromString(uuid)
        case _              => throw new DeserializationException("Expected hexadecimal UUID string")
      }
    }
  }

//  implicit val userIdentifierJsonFormat = jsonFormat1(UserIdentifier))
  implicit object userIdentifierJsonFormat extends RootJsonFormat[UserIdentifier] {
  def write(identifier: UserIdentifier) = JsObject("user_id" -> JsString(identifier.userId.toString))
  def read(value: JsValue): UserIdentifier = value match {
    case JsString(uuid) => UserIdentifier(UUID.fromString(uuid))
    case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }
//  implicit val userIdentifierJsonFormat = jsonFormat1(UserIdentifier)

//  implicit val userJsonFormat = jsonFormat2(User)
  implicit object UserJsonFormat extends RootJsonFormat[User]{
    def write(user: User) = JsObject("user_id" -> JsString(user.userId.userId.toString), "credit" -> JsNumber(user.credit))
    def read(value: JsValue) = {
      value.asJsObject.getFields("user_id", "credit") match {
        case Seq(JsString(userId), JsNumber(credit)) => User(UserIdentifier(UUID.fromString(userId)), credit.toLong)
        case _ => throw DeserializationException("User expected")
      }
    }
  }
//  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)

  implicit object orderIdentifierJsonFormat extends RootJsonFormat[OrderIdentifier] {
    def write(identifier: OrderIdentifier) = JsObject("order_id" -> JsString(identifier.orderId.toString))
    def read(value: JsValue): OrderIdentifier = value match {
      case JsString(uuid) => OrderIdentifier(UUID.fromString(uuid))
      case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }

  implicit object OrderJsonFormat extends RootJsonFormat[StoredOrder]{
    def write(order: StoredOrder) = JsObject("order_id" -> JsString(order.orderId.orderId.toString),
      "user_id" -> JsString(order.order.userId.userId.toString), "total_cost" -> JsNumber(order.order.totalCost),
      "paid" -> {
        if (order.status.equals(OrderStatus.OrderPaid)) { JsString("true")} else {JsString("false")}
      })
//      "items" -> JsArray(order.order.items.map(id => jsonFormat1(id)).toVector))
    def read(value: JsValue): StoredOrder = {
      value.asJsObject.getFields("order_id", "total_cost", "user_id", "paid") match {
        case Seq(JsString(orderId), JsNumber(totalCost), JsString(userId), JsString(paid)) =>
          StoredOrder(OrderIdentifier(UUID.fromString(orderId)), OrderStatus.New,
            Order(UserIdentifier(UUID.fromString(userId)), List(), totalCost.toLong))
        case _ => throw DeserializationException("User expected")
      }
    }
  }
}
