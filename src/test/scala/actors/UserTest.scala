
package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class UserTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "User" must {
    "create a user" in {
      val user = testKit.spawn(User("1"))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
    }
    
    "find a user" in {
      val user = testKit.spawn(User("2"))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User("2", 0))
    }
    
    "add credit" in {
      val user = testKit.spawn(User("3"))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.AddCredit(100, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User("3", 100))
    }

    "subtract credit" in {
      val user = testKit.spawn(User("4"))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.AddCredit(100, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.SubtractCredit(50, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User("4", 50))
    }
    
    "not be able to find a user which does not exist" in {
      val user = testKit.spawn(User("DOESNOTEXIST"))
      val probe = testKit.createTestProbe[User.UserResponse]()
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.Failed("Couldn't find " + "user-" + "DOESNOTEXIST"))
    }
    
//    "not be able to update credit for non-existing user" in {
//      val user = testKit.spawn(User("DOESNOTEXIST2"))
//      val probe = testKit.createTestProbe[User.UserResponse]()
//      user ! User.AddCredit(50, probe.ref)
//      probe.expectMessage(User.Successful())
//      user ! User.FindUser(probe.ref)
//      probe.expectMessage(User.User("DOESNOTEXIST2", 50))
//    }
  }
  
  "User" must {
    val id = "user"
    val user = testKit.spawn(User(id))
    val probe = testKit.createTestProbe[User.UserResponse]()
    
    "create in a single actor" in {
      user ! User.CreateUser(probe.ref)
      probe.expectMessage(User.Successful())
    }
    
    "find in a single actor" in {
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 0))
    }
    
    "add credit in a single actor" in {
      user ! User.AddCredit(100, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 100))
    }
    
    "subtract credit in a single actor" in {
      user ! User.SubtractCredit(50, probe.ref)
      probe.expectMessage(User.Successful())
      user ! User.FindUser(probe.ref)
      probe.expectMessage(User.User(id, 50))
    }
  }
}