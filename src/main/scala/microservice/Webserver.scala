package microservice

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import scala.concurrent.Future

class Webserver(implicit val system:ActorSystem)
  extends Userservice
    with Orderservice
    with Paymentservice
    with Stockservice {
  def startServer(address:String, port:Int): Future[Http.ServerBinding] = {
    Http().bindAndHandle(
      concat(
        userRoutes,
        orderRoutes,
        stockRoutes,
        paymentRoutes
      ),
      address,
      port
    )
  }
}

object Webserver {
  def main(args: Array[String]): Unit = {

    implicit val actorSystem: ActorSystem = ActorSystem("rest-server")
    val server = new Webserver()
    server.startServer("localhost",8080)
  }
}