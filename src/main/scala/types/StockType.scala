package types

import akka.cluster.ddata.{PNCounter, ReplicatedData, SelfUniqueAddress}

final class StockType(val item_id: Long, stock: PNCounter, val price: Long) extends ReplicatedData {
  override type T = this.type

  def increment(n: Long)(implicit node: SelfUniqueAddress): StockType = {
    copy(stock = this.stock.increment(n))
  }

  def decrement(n: Long)(implicit node: SelfUniqueAddress): StockType = {
    copy(stock = this.stock.decrement(n))
  }

  override def merge(that: StockType.this.type): StockType.this.type = {
    copy(stock = that.stock.merge(this.stock))
  }

  private def copy(item_id: Long = this.item_id, stock: PNCounter = this.stock, price: Long = this.price): StockType =
    new StockType(item_id, stock, price)

  def stockValue: BigInt = stock.value
}