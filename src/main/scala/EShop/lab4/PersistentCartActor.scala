package EShop.lab4

import EShop.lab2.Checkout.CheckOutClosed
import EShop.lab2.{Cart, Checkout}
import akka.actor.{ActorSystem, Cancellable, Props}
import akka.event.Logging
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  val cartTimerDuration = 5.seconds
  val system = ActorSystem("Lab4")
  private val log       = Logging(context.system, this)

  override def receiveCommand: Receive = empty

  override def receiveRecover: Receive = {
    case evt: Event =>
      updateState(evt)
  }

  def empty: Receive = {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty)) { event =>
        updateState(event)
      }
    case GetItems =>
      sender ! Cart.empty
  }

  def inCheckout(cart: Cart): Receive = {
    case CheckoutStarted(checkoutActor, cart) =>
      persist(CheckoutStarted(checkoutActor, cart)) { event =>
        updateState(event)
      }
    case CloseCheckout =>
      persist(CheckoutClosed) { event =>
        updateState(event)
      }
    case CheckOutClosed =>
      persist(CheckoutClosed) { event =>
        updateState(event)
      }
    case CancelCheckout =>
      persist(CheckoutCancelled(cart)) { event =>
        updateState(event)
      }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case AddItem(item) =>
      timer.cancel()
      persist(ItemAdded(item, cart)) { event =>
        updateState(event)
      }
    case RemoveItem(item) =>
      timer.cancel()
      persist(ItemRemoved(item, cart)) { event =>
        updateState(event)
      }
    case StartCheckout if cart.size > 0 =>
      val checkoutActor = context.actorOf(Props(new Checkout(self)), "checkout")
      persist(CheckoutStarted(checkoutActor, cart)) { event =>
        checkoutActor ! Checkout.StartCheckout
        sender ! event
        updateState(event)
      }
    case GetItems =>
      sender ! cart
    case ExpireCart =>
      context become empty
  }

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit =
    context.become(event match {
      case CartExpired | CheckoutClosed => empty
      case CheckoutCancelled(cart) => nonEmpty(cart, scheduleTimer)
      case ItemAdded(item, cart) => nonEmpty(cart.addItem(item), scheduleTimer)
      case CartEmptied => empty
      case ItemRemoved(item, cart) if cart.size == 1 && cart.contains(item) => empty
      case ItemRemoved(item, cart) if cart.size > 1 => nonEmpty(cart.removeItem(item), scheduleTimer)
      case CheckoutStarted(_, cart) => inCheckout(cart)
    })

  private def scheduleTimer: Cancellable = system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)
}
