package microservice.actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class StockTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Stock" must {
    "create a stock" in {
      var stock = testKit.spawn(Stock("1"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(12, probe.ref)
      probe.expectMessage(Stock.Successful("1"))
      stock = testKit.spawn(Stock("1"))
    }

    "find a stock" in {
      var stock = testKit.spawn(Stock("2"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(12, probe.ref)
      probe.expectMessage(Stock.Successful("2"))
      stock = testKit.spawn(Stock("2"))
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock("2", 0, 12))
      stock = testKit.spawn(Stock("2"))
    }

    "add stock" in {
      var stock = testKit.spawn(Stock("3"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(1, probe.ref)
      probe.expectMessage(Stock.Successful("3"))
      stock = testKit.spawn(Stock("3"))
      stock ! Stock.AddStock(100, probe.ref)
      probe.expectMessage(Stock.Successful("3"))
      stock = testKit.spawn(Stock("3"))
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock("3", 100, 1))
      stock = testKit.spawn(Stock("3"))
    }

    "subtract stock" in {
      var stock = testKit.spawn(Stock("4"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(1, probe.ref)
      probe.expectMessage(Stock.Successful("4"))
      stock = testKit.spawn(Stock("4"))
      stock ! Stock.AddStock(100, probe.ref)
      probe.expectMessage(Stock.Successful("4"))
      stock = testKit.spawn(Stock("4"))
      stock ! Stock.SubtractStock(30, probe.ref)
      probe.expectMessage(Stock.Successful("4"))
      stock = testKit.spawn(Stock("4"))
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock("4", 70, 1))
      stock = testKit.spawn(Stock("4"))
    }

    "not subtract stock if it is not present" in {
      var stock = testKit.spawn(Stock("5"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.CreateStock(1, probe.ref)
      probe.expectMessage(Stock.Successful("5"))
      stock = testKit.spawn(Stock("5"))
      stock ! Stock.AddStock(10, probe.ref)
      probe.expectMessage(Stock.Successful("5"))
      stock = testKit.spawn(Stock("5"))
      stock ! Stock.SubtractStock(30, probe.ref)
      probe.expectMessage(Stock.NotEnoughStock("5"))
      stock = testKit.spawn(Stock("5"))
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock("5", 10, 1))
      stock = testKit.spawn(Stock("5"))
    }

    "not be able to find a stock which does not exist" in {
      var stock = testKit.spawn(Stock("DOESNOTEXIST"))
      val probe = testKit.createTestProbe[Stock.StockResponse]()
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Failed("Couldn't find " + "stock-" + "DOESNOTEXIST", "DOESNOTEXIST"))
      stock = testKit.spawn(Stock("DOESNOTEXIST"))
    }
  }

  "Stock" must {
    val id = "stock"
    var stock = testKit.spawn(Stock(id))
    val probe = testKit.createTestProbe[Stock.StockResponse]()
    val price = 2

    "create in a single actor" in {
      stock ! Stock.CreateStock(price, probe.ref)
      probe.expectMessage(Stock.Successful(id))
      stock = testKit.spawn(Stock(id))
    }

    "find in a single actor" in {
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock(id, 0, price))
      stock = testKit.spawn(Stock(id))
    }

    "add stock in a single actor" in {
      stock ! Stock.AddStock(100, probe.ref)
      probe.expectMessage(Stock.Successful(id))
      stock = testKit.spawn(Stock(id))
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock(id, 100, price))
      stock = testKit.spawn(Stock(id))
    }

    "subtract stock in a single actor" in {
      stock ! Stock.SubtractStock(50, probe.ref)
      probe.expectMessage(Stock.Successful(id))
      stock = testKit.spawn(Stock(id))
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock(id, 50, price))
      stock = testKit.spawn(Stock(id))
    }

    "refuse too much stock in a single actor" in {
      stock ! Stock.SubtractStock(100, probe.ref)
      probe.expectMessage(Stock.NotEnoughStock(id))
      stock = testKit.spawn(Stock(id))
      stock ! Stock.FindStock(probe.ref)
      probe.expectMessage(Stock.Stock(id, 50, price))
      stock = testKit.spawn(Stock(id))
    }
  }
}