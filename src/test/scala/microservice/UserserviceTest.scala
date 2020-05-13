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
        responseAs[String] should include regex "{\"id\":\"*\"}"
      }
    }

  }
}
