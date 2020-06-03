
package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

object IDGenerator {
  private var i = 0
  def getID(): String = {
    i += 1
    i.toString
  }
}

class UserTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "User" must {
    "create a user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
    }
    
    "find a user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 0))
    }
    
    "add credit" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.AddCredit(100, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 100))
    }

    "subtract credit" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.AddCredit(100, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.SubtractCredit(50, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 50))
    }
    
    "not be able to find a user which does not exist" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.Failed("Couldn't find " + "user-" + id))
    }
    
    "throw an error when trying to subtract more credit than user has" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.AddCredit(10, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.SubtractCredit(11, probe.ref)
      probe.expectMessage(User.NotEnoughCredit())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 10))
    }
    
    "delete a user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.DeleteUser(probe.ref)
      probe.expectMessage(User.Successful())
    }
    
    "not find a deleted user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.DeleteUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.Failed("Couldn't find " + "user-" + id))
    }

    "not recreate a deleted user" in {
      val id = IDGenerator.getID()
      val user = testKit.spawn(User(id))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.DeleteUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Failed("A deleted key can not be used again: user-" + id))
    }
  }
  
  "Single user actor" must {
    val id = IDGenerator.getID()
    val user = testKit.spawn(User(id))
    val probe = testKit.createTestProbe[User.UserResponse]()
    
    "create" in {
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
    }
    
    "find" in {
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 0))
    }
    
    "add credit" in {
      user ! User.AddCredit(100, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 100))
    }
    
    "subtract credit" in {
      user ! User.SubtractCredit(50, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 50))
    }
    
    "refuse subtracting too much credit" in {
      user ! User.SubtractCredit(51, probe.ref)
      probe.expectMessage(User.NotEnoughCredit())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 50))
    }
    
    "delete an actor" in {
      user ! User.DeleteUser(probe.ref)
      probe.expectMessage(User.Successful())
    }
    
    "be unable to find deleted" in {
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.Failed("Couldn't find " + "user-" + id))
    }
    
    "not be able to recreate user" in {
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Failed("A deleted key can not be used again: user-" + id))
    }
  }
}