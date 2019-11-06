package EShop.lab2

import EShop.lab2.CartActor._
import EShop.lab2.CartFSM.Status
import akka.actor.{LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }

  sealed trait Data

  case class CartData(cart: Cart) extends Data

  case object Uninitialized extends Data
  def props() = Props(new CartFSM())
}

class CartFSM extends LoggingFSM[Status.Value, Cart] {
  import EShop.lab2.CartFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val cartTimerDuration: FiniteDuration = 1 seconds

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), _) =>
      goto(NonEmpty).using(Cart.empty.addItem(item))
    case _ => stay
  }

  when(NonEmpty, stateTimeout = cartTimerDuration) {
    case Event(AddItem(item), items) =>
      log.info("Added item: " + item + " to cart.")
      stay.using(items.addItem(item))
    case Event(RemoveItem(item), items) if items.size > 1 && items.contains(item) =>
      log.info("Removed item: " + item + " from cart.")
      stay.using(items.removeItem(item))
    case Event(RemoveItem(item), items) if items.size == 1 && items.contains(item) =>
      log.info("Removed item: " + item + ", cart is empty.")
      goto(Empty).using(Cart.empty)
    case Event(CartActor.StartCheckout, items) =>
      log.info("Cart waits for checkout")
      val checkoutActor = context.actorOf(Props(new CheckoutFSM(self)), "checkout")
      sender ! CheckoutStarted(checkoutActor)
      goto(InCheckout).using(items)
    case Event(StateTimeout, _) =>
      log.debug("Cart expired, items will we removed.")
      goto(Empty).using(Cart.empty)
  }

  when(InCheckout) {
    case Event(CheckoutStarted(checkoutActor), _) =>
      log.debug("Checkout started.")
      //      checkoutActor ! Checkout.StartCheckout
      //      checkoutActor ! Checkout.SelectDeliveryMethod("cheap!")
      //      checkoutActor ! Checkout.SelectPayment("fast!")
      //      checkoutActor ! Checkout.ReceivePayment
      stay
    case Event(CloseCheckout, _) =>
      log.debug("Cart checkout was successful, order is closed.")
      goto(Empty).using(Cart.empty)
    case Event(CancelCheckout, items) =>
      log.debug("Cart checkout was not successful, return to order.")
      goto(NonEmpty).using(items)
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unexpected request {} in state {}/{}", e, stateName, s)
      stay
  }
}
