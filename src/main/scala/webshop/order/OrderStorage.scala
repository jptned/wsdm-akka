package webshop.order

import akka.cluster.ddata.ReplicatedData


case class OrderStatus(name: String) {
  import OrderStatus._

}


object OrderStatus {
  val New: OrderStatus = OrderStatus("new")
  val OrderPaid: OrderStatus = OrderStatus("order_paid")
  val Rejected: OrderStatus = OrderStatus("rejected")
  val OrderAcquired: OrderStatus = OrderStatus("order_acquired")
  val Failed: OrderStatus = OrderStatus("failed")
  val Done: OrderStatus = OrderStatus("done")
}
