package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

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
  case class CartData(cart: Cart)  extends Data

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  private case object CartTimer

  def props = Props(new CartActor())
}

class CartActor extends Actor with Timers {

  import CartActor._

  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Unit = timers.startSingleTimer(CartTimer, ExpireCart, cartTimerDuration)
  private val log       = Logging(context.system, this)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
    case _ => context become empty
  }

  def nonEmpty(cart: Cart, timer: Unit): Receive = LoggingReceive {
    case AddItem(item) =>
      log.info("Added item: " + item + " to cart.")
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) if cart.size > 1 =>
      log.info("Added item: " + item + " to cart.")
      context become nonEmpty(cart.removeItem(item), scheduleTimer)
    case RemoveItem(item) if cart.size == 1   =>
      log.info("Removed item: " + item + ", cart is empty.")
      context become empty
    case StartCheckout =>
      log.info("Cart waits for checkout")
      timers.cancel(CartTimer)
      sender ! CheckoutStarted(self)
      sender ! CartData(cart)
      context become inCheckout(cart)
    case ExpireCart => context become empty
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CloseCheckout  =>
      log.debug("Cart checkout was successful, order is closed.")
      context become empty
    case CancelCheckout =>
      log.debug("Cart checkout was not successful, return to order.")
      context become nonEmpty(cart, scheduleTimer)
  }
}


