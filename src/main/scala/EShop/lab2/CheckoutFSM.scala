package EShop.lab2

import EShop.lab2.Checkout._
import EShop.lab2.CheckoutFSM.Status
import EShop.lab3.PaymentFSM
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM(cartActor))
}

class CheckoutFSM(cartActor: ActorRef) extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.CheckoutFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private val scheduler = context.system.scheduler

  startWith(NotStarted, Uninitialized)

  when(NotStarted) {
    case Event(StartCheckout, _) =>
      log.info("Checkout process is started")
      goto(SelectingDelivery).using(
        SelectingDeliveryStarted(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
      )
  }

  when(SelectingDelivery) {
    case Event(SelectDeliveryMethod(deliveryType: String), deliveryStarted: SelectingDeliveryStarted) =>
      log.info("Delivery type is set as: " + deliveryType)
      goto(SelectingPaymentMethod).using(deliveryStarted)
    case Event(CancelCheckout, _) =>
      goto(Cancelled)
    case Event(ExpireCheckout, _) =>
      log.info("Selecting delivery was too long, checkout is cancelled.")
      goto(Cancelled)
  }

  when(SelectingPaymentMethod) {
    case Event(SelectPayment(paymentType: String), deliveryStarted: SelectingDeliveryStarted) =>
      deliveryStarted.timer.cancel()
      log.info("Payment type is set as: " + paymentType)
      val paymentActor = context.actorOf(Props(new PaymentFSM(paymentType, context.parent, self)))
      sender ! Checkout.PaymentStarted(paymentActor)
      goto(ProcessingPayment).using(
        ProcessingPaymentStarted(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))
      )
    case Event(StateTimeout, _) =>
      log.info("Selecting payment was too long, checkout is cancelled.")
      goto(Cancelled)
    case Event(CancelCheckout, _) =>
      goto(Cancelled)
    case Event(ExpirePayment, _) =>
      goto(Cancelled)
    case Event(ExpireCheckout, _) =>
      goto(Cancelled)
  }

  when(ProcessingPayment) {
    case Event(ReceivePayment, processingPaymentStarted: ProcessingPaymentStarted) =>
      processingPaymentStarted.timer.cancel()
      log.info("Payment received. Here")
      goto(Closed)
    case Event(StateTimeout, _) =>
      log.info("Executing payment was too long, checkout is cancelled.")
      goto(Cancelled)
    case Event(CancelCheckout, _) =>
      goto(Cancelled)
    case Event(ExpirePayment, _) =>
      goto(Cancelled)
    case Event(ExpireCheckout, _) =>
      goto(Cancelled)
  }

  when(Cancelled) {
    case _ =>
      log.info("Checkout failed and is cancelled.")
      stay
  }

  when(Closed) {
    case _ =>
      log.info("Checkout was successful and is closed.")
      stay
  }
}
