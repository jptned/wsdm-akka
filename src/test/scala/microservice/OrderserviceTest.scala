package microservice

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OrderserviceTest extends AnyWordSpec with Matchers with ScalatestRouteTest with Orderservice {
  "Orderservice" should {
    "return id when creating order" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/orders/create/2",
      )
      postRequest ~> orderRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"order_id\":\"[\\d]+\"\\}$"
      }
    }
    "return status when deleting order" in {
      val postRequest = HttpRequest(
        HttpMethods.DELETE,
        uri = "/orders/remove/5",
      )
      postRequest ~> orderRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"success\":\"(true|false)\"\\}$"
      }
    }
    "return order when finding" in {
      val postRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/orders/find/5",
      )
      postRequest ~> orderRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"order_id\":\"[\\d]+\",\"paid\":\"(true|false)\",\"items\":\\[[\"\\d+\"]*\\],\"user\":\"[\\d]+\",\"total_cost\":\"[\\d]+\"\\}$"
      }
    }
    "return status when adding item" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/orders/addItem/5/5",
      )
      postRequest ~> orderRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"success\":\"(true|false)\"\\}$"
      }
    }
    "return status when deleting item" in {
      val postRequest = HttpRequest(
        HttpMethods.DELETE,
        uri = "/orders/removeItem/5/5",
      )
      postRequest ~> orderRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"success\":\"(true|false)\"\\}$"
      }
    }
    "return status when checking out" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/orders/checkout/5/5",
      )
      postRequest ~> orderRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"success\":\"(true|false)\"\\}$"
      }
    }
  }
}
