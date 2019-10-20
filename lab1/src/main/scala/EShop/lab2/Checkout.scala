package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                        extends Data
  case class SelectingDeliveryStarted(timer: Unit) extends Data
  case class ProcessingPaymentStarted(timer: Unit) extends Data

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

  private case object CheckoutTimerKey
  private case object PaymentTimerKey

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor with Timers {

  import Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  private def checkoutTimer: Unit = timers.startSingleTimer(CheckoutTimerKey, ExpireCheckout, checkoutTimerDuration)

  private def paymentTimer: Unit = timers.startSingleTimer(PaymentTimerKey, ExpirePayment, checkoutTimerDuration)

  def receive: Receive = {
    case StartCheckout =>
      log.debug("started!")
      val timer = checkoutTimer
      sender ! SelectingDeliveryStarted(timer)
      context become selectingDelivery(timer)
  }

  def selectingDelivery(timer: Unit): Receive = LoggingReceive {
    case SelectDeliveryMethod(_) =>
      timers.cancelAll()
      context become selectingPaymentMethod(timer)
    case ExpireCheckout =>
      log.debug("cancelled from selectingDelivery")
      context become cancelled
  }

  def selectingPaymentMethod(timer: Unit): Receive = LoggingReceive {
    case SelectPayment(_) =>
      timers.cancel(CheckoutTimerKey)
      context become processingPayment(paymentTimer)
    case ExpirePayment    =>
      log.debug("cancelled from selectingPayment")
      context become cancelled
  }

  def processingPayment(timer: Unit): Receive = LoggingReceive {
    case ReceivePayment =>
      log.debug("timers cancelled in ReceivePayment")
      context become closed
      timers.cancel(PaymentTimerKey)
      sender ! CheckOutClosed
    case ExpirePayment  =>
      log.debug("cancelled from processingPayment")
      context become cancelled
  }

  def cancelled: Receive = LoggingReceive {
    case _ =>
      log.debug("!! !! called: cancelled")
      sender ! Uninitialized
      context.stop(self)
  }

  def closed: Receive = LoggingReceive {
    case _ =>
      log.debug("!! !! called: closed")
      sender ! CheckOutClosed
      context.stop(self)
  }
}
