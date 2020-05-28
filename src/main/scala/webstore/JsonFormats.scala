package webstore

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}
import webstore.OrderStorage.OrderId
import webstore.UserStorage.{StoredUser, UserId}

trait JsonFormats extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object userIdentifierJsonFormat extends RootJsonFormat[UserId] {
    def write(id: UserId) = JsObject("user_id" -> JsString(id.id))
    def read(value: JsValue): UserId = value match {
      case JsString(uuid) => UserId(uuid)
      case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }

  implicit object UserJsonFormat extends RootJsonFormat[StoredUser]{
    def write(user: StoredUser) = JsObject("user_id" -> JsString(user.id.id), "credit" -> JsNumber(user.credit))
    def read(value: JsValue): StoredUser = {
      value.asJsObject.getFields("user_id", "credit") match {
        case Seq(JsString(userId), JsNumber(credit)) => StoredUser(UserId(userId), credit.toLong)
        case _ => throw DeserializationException("User expected")
      }
    }
  }

  implicit object orderIdentifierJsonFormat extends RootJsonFormat[OrderId] {
    def write(id: OrderId) = JsObject("order_id" -> JsString(id.id))
    def read(value: JsValue): OrderId = value match {
      case JsString(uuid) => OrderId(uuid)
      case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }

}
