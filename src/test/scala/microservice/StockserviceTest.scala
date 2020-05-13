package microservice

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StockserviceTest extends AnyWordSpec with Matchers with ScalatestRouteTest with Stockservice {
  "Stockservice" should {
    "return stock and price when finding" in {
      val postRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/stock/find/2",
      )
      postRequest ~> stockRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"stock\":\"[\\d]+\",\"price\":\"[\\d]+\"\\}$"
      }
    }
    "return result when subtracting" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/stock/subtract/1/2",
      )
      postRequest ~> stockRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"success\":\"(true|false)\"\\}$"
      }
    }
    "return result when adding" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/stock/add/1/2",
      )
      postRequest ~> stockRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"success\":\"(true|false)\"\\}$"
      }
    }
    "return status when creating" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/stock/item/create/42",
      )
      postRequest ~> stockRoutes ~> check {
        status.isSuccess() shouldEqual true
        responseAs[String] should fullyMatch regex "^\\{\"item_id\":\"[\\d]+\"\\}$"
      }
    }
  }
}
