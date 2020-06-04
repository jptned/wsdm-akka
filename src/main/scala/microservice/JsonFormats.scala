package microservice

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import microservice.actors.OrderRequest.{Order, OrderId}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

trait JsonFormats extends DefaultJsonProtocol with SprayJsonSupport {


  implicit object orderIdJsonFormat extends RootJsonFormat[OrderId] {
    def write(id: OrderId) = JsObject("order_id" -> JsString(id.id))
    def read(value: JsValue): OrderId = value match {
      case JsString(uuid) => OrderId(uuid)
      case _ => throw DeserializationException("Expected hexadecimal UUID string")
    }
  }


  implicit object OrderJsonFormat extends RootJsonFormat[Order] {
    def write(order: Order) = JsObject("order_id" -> JsString(order.orderId.id),
      "user_id" -> JsString(order.userId), "total_cost" -> JsNumber(order.totalCost),
      "paid" -> JsBoolean(order.paid), "items" -> JsArray(order.items.map(item => JsString(item)).toVector))
    def read(value: JsValue): Order = {
      value.asJsObject.getFields("order_id", "total_cost", "user_id", "paid") match {
        case Seq(JsString(orderId), JsNumber(totalCost), JsString(userId), JsBoolean(paid)) =>
          Order(OrderId(orderId), userId, List(), totalCost.toLong, paid)
        case _ => throw DeserializationException("Order expected")
      }
    }
  }


}
