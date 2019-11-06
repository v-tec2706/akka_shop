package EShop.lab2

import EShop.lab3.Payment
import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(
  cartActor: ActorRef
              ) extends Actor
  with Timers {

  import Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  def receive: Receive = {
    case StartCheckout =>
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(deliveryType: String) =>
      timers.cancelAll()
      context become selectingPaymentMethod(timer)
    case CancelCheckout =>
      self ! CheckOutClosed
      context become cancelled
    case ExpireCheckout =>
      self ! CheckOutClosed
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(paymentType: String) =>
      val paymentActor = context.actorOf(Props(new Payment(paymentType, context.parent, self)))
      sender ! Checkout.PaymentStarted(paymentActor)
      context become processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))
    case CancelCheckout =>
      self ! CheckOutClosed
      context become cancelled
    case ExpireCheckout =>
      self ! CheckOutClosed
      context become cancelled
    case ExpirePayment =>
      self ! CheckOutClosed
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case PaymentStarted(_) =>
      context become processingPayment(timer)
    case ReceivePayment =>
      timer.cancel()
      context.parent ! CheckOutClosed
      context become closed
    case ExpirePayment =>
      self ! CheckOutClosed
      context become cancelled
    case CancelCheckout =>
      self ! CheckOutClosed
      context become cancelled
  }

  def cancelled: Receive = LoggingReceive {
    case _ =>
      sender ! Uninitialized
      context.stop(self)
  }

  def closed: Receive = LoggingReceive {
    case _ =>
      context.stop(self)
    case CancelCheckout =>
      context.stop(self)
  }
}
