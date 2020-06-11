package microservice.actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.cluster.ddata.typed.scaladsl.DistributedData
import org.scalatest.wordspec.AnyWordSpecLike

class StockActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  implicit val node = DistributedData(testKit.system).selfUniqueAddress
  
  "Stock" must {
    "create a stock" in {
      var stock = testKit.spawn(StockActor("1"))
      val probe = testKit.createTestProbe[StockActor.StockResponse]()
      stock ! StockActor.CreateStock(12, probe.ref)
      probe.expectMessage(StockActor.Successful("1"))
      stock = testKit.spawn(StockActor("1"))
    }

    "find a stock" in {
      var stock = testKit.spawn(StockActor("2"))
      val probe = testKit.createTestProbe[StockActor.StockResponse]()
      stock ! StockActor.CreateStock(12, probe.ref)
      probe.expectMessage(StockActor.Successful("2"))
      stock = testKit.spawn(StockActor("2"))
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock("2", 0, 12))
      stock = testKit.spawn(StockActor("2"))
    }

    "add stock" in {
      var stock = testKit.spawn(StockActor("3"))
      val probe = testKit.createTestProbe[StockActor.StockResponse]()
      stock ! StockActor.CreateStock(1, probe.ref)
      probe.expectMessage(StockActor.Successful("3"))
      stock = testKit.spawn(StockActor("3"))
      stock ! StockActor.AddStock(100, probe.ref)
      probe.expectMessage(StockActor.Successful("3"))
      stock = testKit.spawn(StockActor("3"))
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock("3", 100, 1))
      stock = testKit.spawn(StockActor("3"))
    }

    "subtract stock" in {
      var stock = testKit.spawn(StockActor("4"))
      val probe = testKit.createTestProbe[StockActor.StockResponse]()
      stock ! StockActor.CreateStock(1, probe.ref)
      probe.expectMessage(StockActor.Successful("4"))
      stock = testKit.spawn(StockActor("4"))
      stock ! StockActor.AddStock(100, probe.ref)
      probe.expectMessage(StockActor.Successful("4"))
      stock = testKit.spawn(StockActor("4"))
      stock ! StockActor.SubtractStock(30, probe.ref)
      probe.expectMessage(StockActor.Successful("4"))
      stock = testKit.spawn(StockActor("4"))
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock("4", 70, 1))
      stock = testKit.spawn(StockActor("4"))
    }

    "not subtract stock if it is not present" in {
      var stock = testKit.spawn(StockActor("5"))
      val probe = testKit.createTestProbe[StockActor.StockResponse]()
      stock ! StockActor.CreateStock(1, probe.ref)
      probe.expectMessage(StockActor.Successful("5"))
      stock = testKit.spawn(StockActor("5"))
      stock ! StockActor.AddStock(10, probe.ref)
      probe.expectMessage(StockActor.Successful("5"))
      stock = testKit.spawn(StockActor("5"))
      stock ! StockActor.SubtractStock(30, probe.ref)
      probe.expectMessage(StockActor.NotEnoughStock("5"))
      stock = testKit.spawn(StockActor("5"))
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock("5", 10, 1))
      stock = testKit.spawn(StockActor("5"))
    }

    "not be able to find a stock which does not exist" in {
      var stock = testKit.spawn(StockActor("DOESNOTEXIST"))
      val probe = testKit.createTestProbe[StockActor.StockResponse]()
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Failed("Couldn't find " + "stock-" + "DOESNOTEXIST", "DOESNOTEXIST"))
      stock = testKit.spawn(StockActor("DOESNOTEXIST"))
    }
  }

  "Stock" must {
    val id = "stock"
    var stock = testKit.spawn(StockActor(id))
    val probe = testKit.createTestProbe[StockActor.StockResponse]()
    val price = 2

    "create in a single actor" in {
      stock ! StockActor.CreateStock(price, probe.ref)
      probe.expectMessage(StockActor.Successful(id))
      stock = testKit.spawn(StockActor(id))
    }

    "find in a single actor" in {
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock(id, 0, price))
      stock = testKit.spawn(StockActor(id))
    }

    "add stock in a single actor" in {
      stock ! StockActor.AddStock(100, probe.ref)
      probe.expectMessage(StockActor.Successful(id))
      stock = testKit.spawn(StockActor(id))
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock(id, 100, price))
      stock = testKit.spawn(StockActor(id))
    }

    "subtract stock in a single actor" in {
      stock ! StockActor.SubtractStock(50, probe.ref)
      probe.expectMessage(StockActor.Successful(id))
      stock = testKit.spawn(StockActor(id))
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock(id, 50, price))
      stock = testKit.spawn(StockActor(id))
    }

    "refuse too much stock in a single actor" in {
      stock ! StockActor.SubtractStock(100, probe.ref)
      probe.expectMessage(StockActor.NotEnoughStock(id))
      stock = testKit.spawn(StockActor(id))
      stock ! StockActor.FindStock(probe.ref)
      probe.expectMessage(StockActor.Stock(id, 50, price))
      stock = testKit.spawn(StockActor(id))
    }
  }
}