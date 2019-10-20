package EShop.lab2

import EShop.lab2.Checkout.{CheckOutClosed, Data, ReceivePayment, SelectDeliveryMethod, SelectPayment, SelectingDeliveryStarted, StartCheckout, Uninitialized}
import EShop.lab2.CheckoutFSM.Status
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM)
}

class CheckoutFSM extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.CheckoutFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private val scheduler = context.system.scheduler

  startWith(NotStarted, Uninitialized)

  when(NotStarted, checkoutTimerDuration) {
    case Event(StartCheckout, _) =>
      log.debug("started!")
      sender ! SelectingDeliveryStarted()
      goto(SelectingDelivery)
  }

  when(SelectingDelivery) {
    case Event(SelectDeliveryMethod(_), _) =>
      goto(SelectingPaymentMethod)
  }

  when(SelectingPaymentMethod) {
    case Event(SelectPayment(_), _) =>
      goto(ProcessingPayment)
  }

  when(ProcessingPayment) {
    case Event(ReceivePayment, _) =>
      sender ! CheckOutClosed
      goto(Closed)
  }

  when(Cancelled) {
    case _ =>
      sender ! Uninitialized
      goto(NotStarted)
  }

  when(Closed) {
    case _ =>
      sender ! CheckOutClosed
      goto(NotStarted)
  }
}
