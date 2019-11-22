package EShop.lab4

import EShop.lab3.Payment
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val system = ActorSystem("Lab4")
  val timerDuration     = 1.seconds

  def receiveCommand: Receive = {
    case StartCheckout =>
      log.info("hereeee i am")
      persist(CheckoutStarted) { event =>
        updateState(event)
      }
    case CancelCheckout =>
      log.info("i'm hereeee2222222")
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case Expire =>
      log.info("expired in receive command")
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(deliveryType: String) =>
      timer.cancel()
      persist(DeliveryMethodSelected(deliveryType)) { event =>
        log.info("i'm hereeee111111")
        updateState(event)
      }
    case CancelCheckout =>
      timer.cancel()
      log.info("i'm hereeee2222222")
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case Expire =>
      log.info("expired in selecting delivery")
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(paymentType: String) =>
      timer.cancel()
      val paymentActor = context.actorOf(Props(new Payment(paymentType, context.parent, self)))
      persist(PaymentStarted(paymentActor)) { event =>
        log.info("!!! select payment in selecting payment")
        sender ! event
        updateState(event)
      }
    case CancelCheckout =>
      log.info("!!! cancel checkout in selecting payment")
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case Expire =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case ExpirePayment =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case PaymentStarted(_) =>
    case CancelCheckout =>
      persist(CheckoutCancelled) { event =>
        log.info("!!! cancel checkout in processing payment")

        updateState(event)
      }
    case ReceivePayment =>
      timer.cancel()
      context.parent ! CheckOutClosed
      persist(CheckOutClosed) { event =>
        log.info("!!! receive payment in processing payment")

        updateState(event)
      }
    case ExpireCheckout =>
      log.info("!!! cancel checkout in processing payment")

      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case Expire =>
      persist(CheckoutCancelled) { event =>
        log.info("!!! expire in processing payment")

        updateState(event)
      }
  }

  def cancelled: Receive = LoggingReceive {
    case message =>
      log.info("received in cancelled", message)
  }

  def closed: Receive = LoggingReceive {
    case message =>
      log.info("received in closed", message)
  }

  override def receiveRecover: Receive = {
    case evt: Event => updateState(evt)
  }

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    context.become(event match {
      case CheckoutStarted => selectingDelivery(scheduleTimer)
      case DeliveryMethodSelected(method) => selectingPaymentMethod(scheduleTimer)
      case CheckOutClosed => closed
      case CheckoutCancelled => cancelled
      case PaymentStarted(payment) => processingPayment(scheduleTimer)
    })
  }

  private def scheduleTimer: Cancellable = scheduler.scheduleOnce(timerDuration, self, Expire)

}
