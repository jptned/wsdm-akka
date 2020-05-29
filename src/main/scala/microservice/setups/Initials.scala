package microservice.setups

object Initials {

  case class ItemId(id: String) extends AnyVal
  case class UserId(id: String) extends AnyVal
  case class OrderId(id: String) extends AnyVal
  case class Order(orderId: OrderId, userId: UserId, var items: List[ItemId], var totalCost: Long, var paid: Boolean)

}
