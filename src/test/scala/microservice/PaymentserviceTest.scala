//package microservice
//
//import akka.http.scaladsl.model._
//import akka.http.scaladsl.testkit.ScalatestRouteTest
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//
//class PaymentserviceTest extends AnyWordSpec with Matchers with ScalatestRouteTest with Paymentservice {
//  "Paymentservice" should {
//    "return result when paying" in {
//      val postRequest = HttpRequest(
//        HttpMethods.POST,
//        uri = "/payment/pay/1/2",
//      )
//      postRequest ~> paymentRoutes ~> check {
//        status.isSuccess() shouldEqual true
//        responseAs[String] should fullyMatch regex "^\\{\"success\":(true|false)\\}$"
//      }
//    }
//    "return result when cancelling" in {
//      val postRequest = HttpRequest(
//        HttpMethods.POST,
//        uri = "/payment/cancel/1/2",
//      )
//      postRequest ~> paymentRoutes ~> check {
//        status.isSuccess() shouldEqual true
//        responseAs[String] should fullyMatch regex "^\\{\"success\":(true|false)\\}$"
//      }
//    }
//    "return status when requesting" in {
//      val postRequest = HttpRequest(
//        HttpMethods.GET,
//        uri = "/payment/status/2",
//      )
//      postRequest ~> paymentRoutes ~> check {
//        status.isSuccess() shouldEqual true
//        responseAs[String] should fullyMatch regex "^\\{\"paid\":(true|false)\\}$"
//      }
//    }
//  }
//}
