package microservice

import akka.actor.ActorSystem
import akka.cluster.{Cluster, MemberStatus}
import org.slf4j.LoggerFactory

import scala.concurrent.Future

// Enabled in application.conf
class HealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val log = LoggerFactory.getLogger(getClass)
  private val cluster = Cluster(system)

  override def apply(): Future[Boolean] = Future.successful(cluster.selfMember.status == MemberStatus.Up)
}
