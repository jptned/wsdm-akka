
package microservice.actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

object IDGenerator {
  private var i = 0
  def getID(): String = {
    i += 1
    i.toString
  }
}

class UserActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "User" must {
    "create a user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
    }
    
    "find a user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 0))
    }
    
    "add credit" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.AddCredit(100, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 100))
    }

    "subtract credit" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.AddCredit(100, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.SubtractCredit(50, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 50))
    }
    
    "not be able to find a user which does not exist" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Couldn't find " + "user-" + id))
    }
    
    "throw an error when trying to subtract more credit than user has" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.AddCredit(10, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.SubtractCredit(11, probe.ref)
      probe.expectMessage(UserActor.NotEnoughCredit())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 10))
    }
    
    "delete a user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
    }
    
    "not find a deleted user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Couldn't find " + "user-" + id))
    }

    "not recreate a deleted user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(UserActor(id))
      val probe = testKit.createTestProbe[UserActor.UserResponse]()
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Failed("A deleted key can not be used again: user-" + id))
    }
  }
  
  "Single user actor" must {
    val id = IDGenerator.getID()
    val user = testKit.spawn(UserActor(id))
    val probe = testKit.createTestProbe[UserActor.UserResponse]()
    
    "create" in {
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
    }
    
    "find" in {
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 0))
    }
    
    "add credit" in {
      user ! UserActor.AddCredit(100, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 100))
    }
    
    "subtract credit" in {
      user ! UserActor.SubtractCredit(50, probe.ref)
      probe.expectMessage(UserActor.Successful())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 50))
    }
    
    "refuse subtracting too much credit" in {
      user ! UserActor.SubtractCredit(51, probe.ref)
      probe.expectMessage(UserActor.NotEnoughCredit())
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.User(id, 50))
    }
    
    "delete an actor" in {
      user ! UserActor.DeleteUser(probe.ref)
      probe.expectMessage(UserActor.Successful())
    }
    
    "be unable to find deleted" in {
      user ! UserActor.FindUser(probe.ref)
      probe.expectMessage(UserActor.Failed("Couldn't find " + "user-" + id))
    }
    
    "not be able to recreate user" in {
      user ! UserActor.CreateUser(probe.ref)
      probe.expectMessage(UserActor.Failed("A deleted key can not be used again: user-" + id))
    }
  }
}