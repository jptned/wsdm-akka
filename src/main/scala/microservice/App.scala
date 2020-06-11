package microservice

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.ClusterEvent
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.stream.Materializer
import akka.{actor => classic}
import microservice.actors.OrderManager
import microservice.routes.{OrderRoutes, PaymentRoutes, StockService, UserService}

import scala.concurrent.ExecutionContextExecutor

object App extends App {

  ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
    import akka.actor.typed.scaladsl.adapter._
    implicit val ct: ActorContext[Nothing] = context
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val classicSystem: classic.ActorSystem = system.toClassic
    implicit val ec: ExecutionContextExecutor = context.system.executionContext
    implicit val materializer: Materializer = Materializer(context.system.toClassic)

    val cluster = Cluster(context.system)
    context.log.info("Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    implicit val node = DistributedData(context.system).selfUniqueAddress
    val userservice: UserService = new UserService()
    val stockservice: StockService = new StockService()

    val buildOrderManager = context.spawn(OrderManager(), "OrderManager")
    val orderRoutes = new OrderRoutes(buildOrderManager)(context.system)
    val paymentRoutes = new PaymentRoutes(buildOrderManager)(context.system)

    Http().bindAndHandle(concat(
      userservice.userRoutes,
      stockservice.stockRoutes,
      orderRoutes.orderRoutes,
      paymentRoutes.paymentRoutes,
    ), "0.0.0.0", 8080)

    // Create an actor that handles cluster domain events
    val listener = context.spawn(Behaviors.receiveMessage[ClusterEvent.MemberEvent] {
      event: ClusterEvent.MemberEvent =>
        context.log.info("MemberEvent: {}", event)
        Behaviors.same
    }, "listener")

    Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

    AkkaManagement.get(classicSystem).start()
    ClusterBootstrap.get(classicSystem).start()
    Behaviors.empty
  }, "wsdm-akka")
}
