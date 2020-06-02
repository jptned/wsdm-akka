package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class StockTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Stock" must {
    "create a stock" in {
      val stock = testKit.spawn(Stock("1"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(12, probe.ref)
      probe.expectMessage(Stock.Successful())
    }

    "find a stock" in {
      val stock = testKit.spawn(Stock("2"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(12, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock("2", 0, 12))
    }

    "add stock" in {
      val stock = testKit.spawn(Stock("3"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(1, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.AddStock(100, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock("3", 100, 1))
    }

    "subtract stock" in {
      val stock = testKit.spawn(Stock("4"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(1, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.AddStock(100, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.SubtractStock(31, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock("4", 70, 1))
    }

    "not be able to find a stock which does not exist" in {
      val stock = testKit.spawn(Stock("DOESNOTEXIST"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Failed("Couldn't find " + "stock-" + "DOESNOTEXIST"))
    }

    //    "not be able to update credit for non-existing stock" in {
    //      val stock = testKit.spawn(Stock("DOESNOTEXIST2"))
    //      val probe = testKit.createTestProbe[Stock.StockResponse]()
    //      stock ! Stock.AddCredit(50, probe.ref)
    //      probe.expectMessage(Stock.Successful())
    //      stock ! Stock.FindStock(probe.ref)
    //      probe.expectMessage(Stock.Stock("DOESNOTEXIST2", 50))
    //    }
  }

  "Stock" must {
    val id = "stock"
    val stock = testKit.spawn(Stock(id))
    val probe = testKit.createTestProbe[Stock.StockResponse]()
    val price = 2

    "create in a single actor" in {
      stock ! Stock.CreateStock(price, probe.ref)
      probe.expectMessage(Stock.Successful())
    }

    "find in a single actor" in {
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock(id, 0, price))
    }

    "add credit in a single actor" in {
      stock ! Stock.AddStock(100, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock(id, 100, price))
    }

    "subtract credit in a single actor" in {
      stock ! Stock.SubtractStock(50, probe.ref)
      probe.expectMessage(Stock.Successful())
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock(id, 50, price))
    }
  }
}