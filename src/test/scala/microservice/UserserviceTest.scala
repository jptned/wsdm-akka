package microservice

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import akka.http.scaladsl.model._
import org.scalatest.wordspec.AnyWordSpec

class UserserviceTest extends AnyWordSpec with Matchers with ScalatestRouteTest with Userservice {
  "Userservice" should {
    "return id when creating user" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/users/create",
      )
      postRequest ~> userRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"user_id\":[\\d]+\\}$"
      }
    }
    "return status when deleting user" in {
      val postRequest = HttpRequest(
        HttpMethods.DELETE,
        uri = "/users/remove/5",
      )
      postRequest ~> userRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"success\":(true|false)\\}$"
      }
    }
    "return a user when getting user" in {
      val postRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/users/get/5",
      )
      postRequest ~> userRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"user_id\":[\\d]+\\,\"credit\":[\\d]+\\}$"
      }
    }
    "return success when credit subtracted" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/users/credit/subtract/5/5",
      )
      postRequest ~> userRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"user_id\":[\\d]+\\,\"credit\":[\\d]+\\}$"
      }
    }
    "return success when credit is added" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/users/credit/add/5/5",
      )
      postRequest ~> userRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"user_id\":[\\d]+\\,\"credit\":[\\d]+\\}$"
      }
    }
  }
}
