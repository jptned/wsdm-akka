package webstore

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import webstore.OrderStorage.OrderId
import webstore.Payment._
import webstore.UserStorage.{ChangeCreditResult, UserId}
import java.time.LocalDateTime
import java.util.UUID

object PaymentHandler {

  def apply(storage: ActorRef[UserStorage.Command[_]]): Behavior[PaymentRequest] = Behaviors.setup { context =>
    Behaviors.withStash(1000) { stash =>
      // register with the Receptionist which makes this actor discoverable
      context.system.receptionist ! Receptionist.Register(Key, context.self)

      val storageAdapter: ActorRef[ChangeCreditResult] = context.messageAdapter { response =>
        AdaptedStorageResponse(response)
      }

      def handleRequest: Behavior[PaymentRequest] = {
        Behaviors.receiveMessage {
          case request @ Payment.PaymentProcess(amount, userId, _, _) =>
            storage ! UserStorage.SubtractCredit(userId, amount, storageAdapter)
            retrievingCard(request)
          case _ => Behaviors.unhandled
        }
      }

      def retrievingCard(request: Payment.PaymentProcess): Behavior[PaymentRequest] = {
        Behaviors.receiveMessage {
          case AdaptedStorageResponse(UserStorage.ChangedSucceed) =>
            // a real system would go talk to backend systems using the retrieved data
            // but here, we're just going to validate the request, always
            request.sender ! RequestProcessed(
              Transaction(
                TransactionId(UUID.randomUUID().toString),
                LocalDateTime.now,
                request.amount,
                request.userId,
                request.orderId))

            // we're able to process new requests
            stash.unstashAll(handleRequest)
          case AdaptedStorageResponse(UserStorage.ChangedFailed) =>
            request.sender ! RequestFailed
            Behaviors.same
          case other =>
            if (stash.isFull) {
              context.log.warn("Cannot handle more incoming messages, dropping them.")
            } else {
              stash.stash(other)
            }
            Behaviors.same
        }
      }

      handleRequest
    }
  }

  val Key: ServiceKey[PaymentRequest] = ServiceKey("PaymentProcess")

  sealed trait InternalMessage extends PaymentRequest
  final case class AdaptedStorageResponse(response: UserStorage.ChangeCreditResult) extends InternalMessage

}

object Payment {
  sealed trait PaymentRequest
  final case class PaymentProcess(amount: Long, userId: UserId, orderId: OrderId, sender: ActorRef[PaymentResponse])
    extends PaymentRequest

  sealed trait PaymentResponse
  final case class RequestProcessed(transaction: Transaction) extends PaymentResponse
  final case object RequestFailed extends PaymentResponse

  final case class Transaction(id: TransactionId, timestamp: LocalDateTime, amount: Long, userId: UserId, orderId: OrderId)
  case class TransactionId(id: String) extends AnyVal
}
