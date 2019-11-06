package EShop.lab2

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command

  sealed trait Data
  case class CartData(cart: Cart) extends Data

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  private case object CartTimer

  def props = Props(new CartActor())
}

class CartActor extends Actor{

  import CartActor._

  val cartTimerDuration = 5 seconds
  val system            = ActorSystem("Lab1")
  private def scheduleTimer: Cancellable =
    system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)
  private val log = Logging(context.system, this)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
    case _ =>
      log.info("Unknown message in empty state, remain empty.")
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      log.info("Added item: " + item + " to cart.")
      timer.cancel()
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) if cart.size > 1 && cart.contains(item) =>
      log.info("Removed item: " + item + " from cart.")
      timer.cancel()
      context become nonEmpty(cart.removeItem(item), scheduleTimer)
    case RemoveItem(item) if cart.size == 1 && cart.contains(item) =>
      log.info("Removed item: " + item + ", cart is empty.")
      context become empty
    case StartCheckout if cart.size > 0 =>
      log.info("Cart waits for checkout")
      val checkoutActor = context.actorOf(Props[Checkout], "checkout")
      self ! CheckoutStarted(checkoutActor)
      context become inCheckout(cart)
    case ExpireCart =>
      log.debug("Cart expired, items will we removed.")
      context become empty
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CheckoutStarted(checkoutActor) =>
      log.debug("Checkout started.")
      checkoutActor ! Checkout.StartCheckout
      checkoutActor ! Checkout.SelectDeliveryMethod("cheap!")
      checkoutActor ! Checkout.SelectPayment("fast!")
      checkoutActor ! Checkout.ReceivePayment
    case CloseCheckout =>
      log.debug("Cart checkout was successful, order is closed.")
      context become empty
    case CancelCheckout =>
      log.debug("Cart checkout was not successful, return to order.")
      context become nonEmpty(cart, scheduleTimer)
  }
}
