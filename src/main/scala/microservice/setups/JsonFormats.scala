package microservice.setups

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import microservice.setups.Initials._
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

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

  implicit object OrderJsonFormat extends RootJsonFormat[Order] {
    def write(order: Order) = JsObject("order_id" -> JsString(order.orderId.id),
      "user_id" -> JsString(order.userId.id), "total_cost" -> JsNumber(order.totalCost),
      "paid" -> JsBoolean(order.paid), "items" -> JsArray(order.items.map(item => itemIdJsonFormat.write(item)).toVector))
    def read(value: JsValue): Order = {
      value.asJsObject.getFields("order_id", "total_cost", "user_id", "paid") match {
        case Seq(JsString(orderId), JsNumber(totalCost), JsString(userId), JsBoolean(paid)) =>
          Order(OrderId(orderId), UserId(userId), List(), totalCost.toLong, paid)
        case _ => throw DeserializationException("Order expected")
      }
    }
  }

  implicit object UserJsonFormat extends RootJsonFormat[User]{
    def write(user: User) = JsObject("user_id" -> JsString(user.userId.id), "credit" -> JsNumber(user.credit))
    def read(value: JsValue): User = {
      value.asJsObject.getFields("user_id", "credit") match {
        case Seq(JsString(userId), JsNumber(credit)) => User(UserId(userId), credit.toLong)
        case _ => throw DeserializationException("User expected")
      }
    }
  }

  implicit object ItemJsonFormat extends RootJsonFormat[Item]{
    def write(item: Item) = JsObject("stock" -> JsNumber(item.stock), "price" -> JsNumber(item.price))
    def read(value: JsValue): Item = {
      value.asJsObject.getFields("item_id", "stock", "price") match {
        case Seq(JsString(itemId), JsNumber(stock), JsNumber(price)) => Item(ItemId(itemId), price.toLong, stock.toLong)
        case _ => throw DeserializationException("Item expected")
      }
    }
  }


}