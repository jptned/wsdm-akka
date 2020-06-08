package microservice

import actors.UserActor.User
import akka.actor.typed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.headers.Server
import akka.http.scaladsl.server.Directives._
import akka.io.Tcp.Message
import akka.stream.{ActorMaterializer, Materializer}
import microservice.Userservice
import microservice.actors.OrderManager
import microservice.routes.OrderRoutes

import scala.concurrent.{ExecutionContextExecutor, Future}
object Webserver
     {
  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  case object Stop extends Message

  import akka.actor.typed.scaladsl.adapter._
  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { context =>
    implicit val ct: ActorContext[Message] = context
    implicit val system: typed.ActorSystem[Nothing] = context.system
    implicit val untypedSystem: akka.actor.ActorSystem = context.system.toClassic

    implicit val materializer: Materializer = Materializer(context.system.toClassic)
    implicit val ec: ExecutionContextExecutor = context.system.executionContext

    // Creating instances of the different endpoint services.
    val userservice: Userservice = new Userservice()
    val stockservice: Stockservice = new Stockservice()

    val buildOrderManager = context.spawn(OrderManager(), "OrderManager")
    val orderRoutes = new OrderRoutes(buildOrderManager)(system)

    val serverBinding: Future[Http.ServerBinding] =     Http().bindAndHandle(
      concat(
        userservice.userRoutes,
        stockservice.stockRoutes,
        orderRoutes.orderRoutes,
        //        These are commented because they need to be reformulated for the new structure, uncomment them when done
//        paymentRoutes
      ),
      host,
      port
    )

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          Behaviors.stopped
      }.receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) =>
          throw new RuntimeException("Server not started", cause)
        case Started(binding) =>
          if (wasStopped) context.self ! Stop
          running(binding)
        case Stop =>
          starting(wasStopped = true)
      }

    starting(wasStopped = false)
  }

  def main(args: Array[String]) {
    val system: ActorSystem[Webserver.Message] = ActorSystem(Webserver("127.0.0.1", 8080), "WebServer")
  }
}