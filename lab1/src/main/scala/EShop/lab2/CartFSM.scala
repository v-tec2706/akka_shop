package EShop.lab2

import EShop.lab2.CartActor.{AddItem, CancelCheckout, CheckoutStarted, CloseCheckout, ExpireCart, RemoveItem}
import EShop.lab2.CartFSM.{CartData, Status}
import EShop.lab2.Checkout.StartCheckout
import akka.actor.{LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }


  sealed trait Data
  case object Uninitialized extends Data
  case class CartData(cart: Cart) extends Data
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
      print(items)
      stay.using(items.addItem(item))
    case Event(RemoveItem(item), items) if items.size > 1 =>
      print(items)
      stay.using(items.removeItem(item))
    case Event(RemoveItem(_), items) if items.size == 1   =>
      print(items)
      goto(Empty).using(Cart.empty)
    case Event(StartCheckout, items) =>
      sender ! CheckoutStarted(self)
      goto(InCheckout).using(items)
  }

  when(InCheckout) {
    case Event(CloseCheckout, _)  =>
      log.debug("!! !! Close checkout and become empty")
      goto(Empty)
    case Event(CancelCheckout, items) =>
      log.debug("!! !! Cancel checkout")
      goto(NonEmpty).using(items)
  }

}
