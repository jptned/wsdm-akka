package webshop

import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.javadsl.model.HttpResponse
import akka.http.scaladsl.Http.ServerBinding
import akka.routing.SmallestMailboxPool
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import webshop.user.{UserRepository, UserRoutes}
import webshop.order.{OrderHandler, OrderRoutes}
import webshop.stock.Stock

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

//import akka.actor.Status.Failure
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import scala.concurrent.duration._
import scala.util.{ Failure, Success }


object Server extends App {

  private implicit val actorSystem: ActorSystem = ActorSystem("example-akka-http")
//  private implicit val executor: ExecutionContext = actorSystem.dispatcher
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val stock = actorSystem.actorOf(Stock.props(), "stock")
  private val orderHandler = actorSystem.actorOf(OrderHandler.props(stock), "order_handler")

  val orderBuilder = new OrderRoutes(orderHandler)
  private val routes = orderBuilder.orderRoutes

  Http().bindAndHandle(routes, "localhost", 8080)

  //  import akka.actor.typed.scaladsl.adapter._
//
//  sealed trait Message
//  private final case class StartFailed(cause: Throwable) extends Message
//  private final case class Started(binding: ServerBinding) extends Message
//  case object Stop extends Message
//
//  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx =>
//
//    implicit val system = ctx.system
//    implicit val untypedSystem: akka.actor.ActorSystem = ctx.system.toClassic
//
//    implicit val materializer: Materializer = Materializer(ctx.system.toClassic)
//    implicit val ec: ExecutionContextExecutor = ctx.system.executionContext
//
////    val stockBuilder = ctx.spawn(Stock(), "Stock")
//    val stock = ctx.actorOf(Stock.props())
//    val orderHandler = ctx.actorOf(OrderHandler.props(stock))
//
//
//    val buildUserRepository = ctx.spawn(UserRepository(), "UserRepository")
//    println(buildUserRepository.getClass.getName)
//    val userRoutes = new UserRoutes(buildUserRepository)
//    println("userRoutes.userRoutes ", userRoutes.userRoutes)
//
////    val buildOrderRepository = ctx.spawn(OrderHandler(stock), "OrderHandler")
//    val orderRoutes = new OrderRoutes(orderHandler)
//
//
//    println("orderRoutes.orderRoutes ", orderRoutes.orderRoutes)
//    val serverBinding: Future[Http.ServerBinding] = Http.apply().bindAndHandle(orderRoutes.orderRoutes, host, port)
//    ctx.pipeToSelf(serverBinding) {
//      case Success(binding) => Started(binding)
//      case Failure(ex)      => StartFailed(ex)
//    }
//
//    def running(binding: ServerBinding): Behavior[Message] =
//      Behaviors.receiveMessagePartial[Message] {
//        case Stop =>
//          ctx.log.info(
//            "Stopping server http://{}:{}/",
//            binding.localAddress.getHostString,
//            binding.localAddress.getPort)
//          Behaviors.stopped
//      }.receiveSignal {
//        case (_, PostStop) =>
//          binding.unbind()
//          Behaviors.same
//      }
//
//    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
//      Behaviors.receiveMessage[Message] {
//        case StartFailed(cause) =>
//          throw new RuntimeException("Server failed to start", cause)
//        case Started(binding) =>
//          ctx.log.info(
//            "Server online at http://{}:{}/",
//            binding.localAddress.getHostString,
//            binding.localAddress.getPort)
//          if (wasStopped) ctx.self ! Stop
//          running(binding)
//        case Stop =>
//          // we got a stop message but haven't completed starting yet,
//          // we cannot stop until starting has completed
//          starting(wasStopped = true)
//      }
//    starting(wasStopped = false)
//  }

//  def main(args: Array[String]) {
//
//    implicit val system: ActorSystem = ActorSystem("my-system")
////    implicit val materializer: Materializer = Materializer()
//    implicit val executionContext = system.dispatcher
//    implicit val timeout = Timeout(1.second)
//
//    val stock = system.actorOf(Stock.props())
//    val orderHandler = system.actorOf(OrderHandler.props(stock))
//
//
//    val host = "localhost"
//    val port= 8080
//    val route = OrderRoutes.
////    val system: ActorSystem = ActorSystem(Server("localhost", 8080), "BuildUserServer")
//    Http().bindAndHandle(route.orderRoutes, host, port)
//
//  }

}
