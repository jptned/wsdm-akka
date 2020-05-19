package types

import akka.cluster.ddata.{GCounter, PNCounter, ReplicatedData}

final class StockType(item_id: Long, stock: PNCounter, price: Long) extends ReplicatedData {
  override type T = this.type

  override def merge(that: StockType.this.type): StockType.this.type = {
    copy(stock = that.stock.merge(this.stock))
  }

  private def copy(item_id: Long = this.item_id, stock: PNCounter = this.stock, price: Long = this.price): StockType =
    new StockType(item_id, stock, price)
}