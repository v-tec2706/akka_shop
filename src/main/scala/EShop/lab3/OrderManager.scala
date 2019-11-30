package EShop.lab3

import EShop.lab2.CartActor.StartCheckout
import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager._
import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object OrderManager {
  sealed trait State
  case object Uninitialized extends State
  case object Open          extends State
  case object InCheckout    extends State
  case object InPayment     extends State
  case object Finished      extends State

  sealed trait Command
  case class AddItem(id: String)                                               extends Command
  case class RemoveItem(id: String)                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command
  case object Buy                                                              extends Command
  case object Pay                                                              extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK

  sealed trait Data
  case object Empty                                                            extends Data
  case class CartData(cartRef: ActorRef)                                       extends Data
  case class CartDataWithSender(cartRef: ActorRef, sender: ActorRef)           extends Data
  case class InCheckoutData(checkoutRef: ActorRef)                             extends Data
  case class InCheckoutDataWithSender(checkoutRef: ActorRef, sender: ActorRef) extends Data
  case class InPaymentData(paymentRef: ActorRef)                               extends Data
  case class InPaymentDataWithSender(paymentRef: ActorRef, sender: ActorRef)   extends Data
}

class OrderManager extends Actor with Timers {

  val resendTimeDuration: FiniteDuration = 0.000005 milliseconds
  private val scheduler = context.system.scheduler
  private val log = Logging(context.system, this)
  var timer: Cancellable = _

  override def receive: Receive = uninitialized

  def uninitialized: Receive = {
    case AddItem(item) =>
      val cartActor = context.actorOf(Props[CartActor], "cartActor")
      cartActor ! CartActor.AddItem(item)
      sender ! Done
      context become open(cartActor)
  }

  def open(cartActor: ActorRef): Receive = LoggingReceive {
    case AddItem(item) =>
      cartActor ! CartActor.AddItem(item)
      sender ! Done
    case RemoveItem(item) =>
      cartActor ! RemoveItem(item)
      sender ! Done
    case Buy =>
      cartActor ! StartCheckout
      context become inCheckout(cartActor, sender)
  }

  def inCheckout(cartActorRef: ActorRef, senderRef: ActorRef): Receive = LoggingReceive {
    case CartActor.CheckoutStarted(checkoutRef: ActorRef, cart) =>
      checkoutRef ! Checkout.StartCheckout
      context become inCheckout(checkoutRef)
      senderRef ! Done
  }

  def inCheckout(checkoutActorRef: ActorRef): Receive = LoggingReceive {
    case SelectDeliveryAndPaymentMethod(delivery: String, payment: String) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! Checkout.SelectPayment(payment)
      context become inPayment(sender)
  }

  def inPayment(senderRef: ActorRef): Receive = {
    case Checkout.PaymentStarted(paymentRef: ActorRef) =>
      senderRef ! Done
      context become inPayment(paymentRef, senderRef)
  }

  def inPayment(paymentActorRef: ActorRef, senderRef: ActorRef): Receive = LoggingReceive {
    case Pay =>
      sender ! Done
      context become finished
      paymentActorRef ! Payment.DoPayment
    case Payment.PaymentConfirmed =>
      context become finished
  }

  def finished: Receive = {
    case _ => sender ! "order manager finished job"
  }
}
