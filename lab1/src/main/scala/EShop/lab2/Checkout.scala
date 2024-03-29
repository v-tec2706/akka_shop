package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                        extends Data
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

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor with Timers {

  import Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  def receive: Receive = {
    case StartCheckout =>
      log.info("Checkout process is started")
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(deliveryType: String) =>
      log.info("Delivery type is set as: " + deliveryType)
      timers.cancelAll()
      context become selectingPaymentMethod(timer)
    case CancelCheckout =>
      self ! CheckOutClosed
      context become cancelled
    case ExpireCheckout =>
      log.info("Selecting delivery was too long, checkout is cancelled.")
      self ! CheckOutClosed
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(paymentType: String) =>
      log.info("Payment type is set as: " + paymentType)
      context become processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))
    case CancelCheckout =>
      self ! CheckOutClosed
      context become cancelled
    case ExpireCheckout =>
      self ! CheckOutClosed
      context become cancelled
    case ExpirePayment =>
      log.info("Selecting payment was too long, checkout is cancelled.")
      self ! CheckOutClosed
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case PaymentStarted(_) =>
      context become processingPayment(timer)
    case ReceivePayment =>
      timer.cancel()
      log.info("Payment received.")
      self ! CheckOutClosed
      context become closed
    case ExpirePayment =>
      log.info("Executing payment was too long, checkout is cancelled.")
      self ! CheckOutClosed
      context become cancelled
    case CancelCheckout =>
      self ! CheckOutClosed
      context become cancelled
  }

  def cancelled: Receive = LoggingReceive {
    case _ =>
      log.info("Checkout failed and is cancelled.")
      sender ! Uninitialized
      context.stop(self)
  }

  def closed: Receive = LoggingReceive {
    case _ =>
      log.info("Checkout was successful and is closed.")
      context.stop(self)
    case CancelCheckout =>
      log.info("Checkout cannot be canceled now!")
      context.stop(self)
  }
}
