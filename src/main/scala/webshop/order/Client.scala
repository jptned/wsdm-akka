//package webshop.order
//
//import akka.actor.{Actor, ActorLogging, ActorRef, Props}
//import webshop.order.OrderHandler.OrderIdentifier
//
//class Client(orderHandler: ActorRef) extends Actor with ActorLogging {
//
//  import Client._
//
//  def receive: Receive = {
//    case CreateOrder(orderId) =>
//      orderHandler forward OrderHandler.CreateOrder(orderId)
//      context.
//  }
//
//}
//
//object Client {
//
//  def props(orderHandler: ActorRef) = Props(new Client(orderHandler))
//
//  case class CreateOrder(orderId: OrderIdentifier)
//
//}
