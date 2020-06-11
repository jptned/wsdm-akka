
package microservice.actors

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import org.scalatest.wordspec.AnyWordSpecLike

object IDGenerator {
  private var i = 0

  def getID: String = {
    i += 1
    i.toString
  }
}

class UserActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  implicit val node = DistributedData(testKit.system).selfUniqueAddress
  
  "User" must {
    "create a user" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
    }

    "find a user" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 0))
      user = testKit.spawn(UserActor(id))
    }

    "add credit" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.AddCredit(100, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 100))
      user = testKit.spawn(UserActor(id))
    }

    "subtract credit" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.AddCredit(100, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.SubtractCredit(50, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 50))
      user = testKit.spawn(UserActor(id))
    }

    "not be able to find a user which does not exist" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Couldn't find " + "user-" + id))
      user = testKit.spawn(UserActor(id))
    }

    "throw an error when trying to subtract more credit than user has" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.AddCredit(10, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.SubtractCredit(11, probe.ref)
      probe.expectMessage(UserActor.NotEnoughCredit())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 10))
      user = testKit.spawn(UserActor(id))
    }

    "delete a user" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
    }

    "not find a deleted user" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Couldn't find " + "user-" + id))
      user = testKit.spawn(UserActor(id))
    }

    "not recreate a deleted user" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Failed("A deleted key can not be used again: user-" + id))
      user = testKit.spawn(UserActor(id))
    }

    "not delete a deleted user" in {
      val id = IDGenerator.getID
      var user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Failed deleting: user-" + id))
      user = testKit.spawn(UserActor(id))
    }
  }

  "Single user actor" must {
    val id = IDGenerator.getID
    var user = testKit.spawn(UserActor(id))
    val probe = testKit.createTestProbe[UserActor.UserResponse]()

    "create" in {
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
    }

    "find" in {
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 0))
      user = testKit.spawn(UserActor(id))
    }

    "add credit" in {
      user ! UserActor.AddCredit(100, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 100))
      user = testKit.spawn(UserActor(id))
    }

    "subtract credit" in {
      user ! UserActor.SubtractCredit(50, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 50))
      user = testKit.spawn(UserActor(id))
    }

    "refuse subtracting too much credit" in {
      user ! UserActor.SubtractCredit(51, probe.ref)
      probe.expectMessage(UserActor.NotEnoughCredit())
      user = testKit.spawn(UserActor(id))
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 50))
      user = testKit.spawn(UserActor(id))
    }

    "delete an actor" in {
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user = testKit.spawn(UserActor(id))
    }

    "be unable to find deleted" in {
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Couldn't find " + "user-" + id))
      user = testKit.spawn(UserActor(id))
    }

    "not be able to recreate user" in {
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Failed("A deleted key can not be used again: user-" + id))
      user = testKit.spawn(UserActor(id))
    }

    "not be able to delete user again" in {
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Failed deleting: user-" + id))
      user = testKit.spawn(UserActor(id))
    }
  }
}