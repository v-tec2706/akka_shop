package EShop.lab2

import EShop.lab2.Checkout.CheckOutClosed
import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
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
  case object GetItems             extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds
  val system = ActorSystem("Lab1")
  private val log = Logging(context.system, this)

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
    case GetItems =>
      sender ! Cart.empty
    case _ =>
  }

  def receive: Receive = empty

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      timer.cancel()
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) if cart.size > 1 && cart.contains(item) =>
      timer.cancel()
      context become nonEmpty(cart.removeItem(item), scheduleTimer)
    case RemoveItem(item) if cart.size == 1 && cart.contains(item) =>
      context become empty
    case StartCheckout if cart.size > 0 =>
      val checkoutActor = context.actorOf(Props(new Checkout(self)), "checkout")
      sender ! CheckoutStarted(checkoutActor)
      context become inCheckout(cart)
    case GetItems =>
      sender ! cart
    case ExpireCart =>
      context become empty
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CheckoutStarted(checkoutActor) =>
    case CloseCheckout =>
      context become empty
    case CheckOutClosed =>
      context become empty
    case CancelCheckout =>
      context become nonEmpty(cart, scheduleTimer)
  }

  private def scheduleTimer: Cancellable =
    system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)
}
