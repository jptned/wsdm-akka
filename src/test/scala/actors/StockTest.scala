package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class StockTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Stock" must {
    "create an item" in {
      val stock = testKit.spawn(Stock("id123"), "Test")
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(12, probe.ref)
      probe.expectMessage(Stock.Successful())
    }
  }
}
