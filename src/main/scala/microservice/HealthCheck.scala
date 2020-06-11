package microservice

import akka.actor.ActorSystem
import org.slf4j.LoggerFactory

import scala.concurrent.Future

// Enabled in application.conf
class HealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val log = LoggerFactory.getLogger(getClass)

  override def apply(): Future[Boolean] = {
    log.info("DemoHealthCheck called")
    Future.successful(true)
  }
}
