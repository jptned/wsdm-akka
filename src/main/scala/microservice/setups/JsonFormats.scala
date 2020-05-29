package microservice.setups

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import microservice.setups.Initials.{ItemId, OrderId, UserId}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

trait JsonFormats extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object userIdJsonFormat extends RootJsonFormat[UserId] {
    def write(id: UserId) = JsObject("user_id" -> JsString(id.id))
    def read(value: JsValue): UserId = value match {
      case JsString(uuid) => UserId(uuid)
      case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }

  implicit object orderIdJsonFormat extends RootJsonFormat[OrderId] {
    def write(id: OrderId) = JsObject("order_id" -> JsString(id.id))
    def read(value: JsValue): OrderId = value match {
      case JsString(uuid) => OrderId(uuid)
      case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }

  implicit object itemIdJsonFormat extends RootJsonFormat[ItemId] {
    def write(id: ItemId) = JsObject("item_id" -> JsString(id.id))
    def read(value: JsValue): ItemId = value match {
      case JsString(uuid) => ItemId(uuid)
      case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }

}