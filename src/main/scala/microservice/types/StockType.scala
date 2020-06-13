package microservice.types

import akka.cluster.ddata._

object StockType {
  def create(item_id: String, price: Long): StockType = new StockType(item_id, PNCounter.empty, price)
}

final case class NotEnoughStockException(private val message: String = "",
                                         private val cause: Throwable = None.orNull) extends Exception(message, cause)

@SerialVersionUID(1L)
final class StockType(val item_id: String, val stock: PNCounter, val price: Long) extends ReplicatedData with ReplicatedDataSerialization {
  override type T = StockType

  def increment(n: Long)(implicit node: SelfUniqueAddress): StockType = {
    copy(stock = this.stock.increment(n))
  }

  def decrement(n: Long)(implicit node: SelfUniqueAddress): StockType = {
    if (this.stock.value < n) throw NotEnoughStockException()
    copy(stock = this.stock.decrement(n))
  }

  override def merge(that: StockType): StockType = {
    copy(stock = that.stock.merge(this.stock))
  }

  private def copy(item_id: String = this.item_id, stock: PNCounter = this.stock, price: Long = this.price): StockType =
    new StockType(item_id, stock, price)

  def stockValue: BigInt = stock.value
}

@SerialVersionUID(1L)
final case class StockTypeKey(_id: String) extends Key[StockType](_id) with ReplicatedDataSerialization
