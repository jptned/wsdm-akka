package webshop

import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.javadsl.model.HttpResponse
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.{ActorMaterializer, Materializer}
import webshop.user.{UserRepository, UserRoutes}

import scala.concurrent.{ExecutionContextExecutor, Future}

//import akka.actor.Status.Failure
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import scala.util.{ Failure, Success }


object Server {

  import akka.actor.typed.scaladsl.adapter._

  sealed trait Message
  private final case class StartFailed(cause: Throwable) extends Message
  private final case class Started(binding: ServerBinding) extends Message
  case object Stop extends Message

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx =>

    implicit val system = ctx.system
    implicit val untypedSystem: akka.actor.ActorSystem = ctx.system.toClassic

    implicit val materializer: Materializer = Materializer(ctx.system.toClassic)
    implicit val ec: ExecutionContextExecutor = ctx.system.executionContext

    val buildUserRepository = ctx.spawn(UserRepository(), "UserRepository")
    val userRoutes = new UserRoutes(buildUserRepository)

    val serverBinding: Future[Http.ServerBinding] = Http.apply().bindAndHandle(userRoutes.userRoutes, host, port)
    ctx.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex)      => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          ctx.log.info(
            "Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          Behaviors.stopped
      }.receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) =>
          throw new RuntimeException("Server failed to start", cause)
        case Started(binding) =>
          ctx.log.info(
            "Server online at http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          if (wasStopped) ctx.self ! Stop
          running(binding)
        case Stop =>
          // we got a stop message but haven't completed starting yet,
          // we cannot stop until starting has completed
          starting(wasStopped = true)
      }
    starting(wasStopped = false)
  }

  def main(args: Array[String]) {
    val system: ActorSystem[Server.Message] = ActorSystem(Server("localhost", 8080), "BuildUserServer")
  }

}



//def main(args: Array[String]): Unit =  {
//  val system: ActorSystem[Server.Message] = ActorSystem(Server("localhost", 8080), "BuildUserServer")
//}
