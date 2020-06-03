package microservice.setups

object Initials {

  case class ItemId(id: String) extends AnyVal
  case class UserId(id: String) extends AnyVal
  case class OrderId(id: String) extends AnyVal

  case class Order(orderId: OrderId, userId: UserId, var items: List[ItemId], var totalCost: Long, var paid: Boolean)
  case class User(userId: UserId, var credit: Long)
  case class Item(itemId: ItemId, price: Long, var stock: Long)

  case class UserStorageEntityId(id: String) extends AnyVal
  case class InventoryEntityId(id: String) extends AnyVal

}
