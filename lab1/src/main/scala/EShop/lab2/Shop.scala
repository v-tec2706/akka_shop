package EShop.lab2

import EShop.lab2.CartActor.{CancelCheckout, CartData}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Shop {
  case class Item(itemName: String)
}

class Shop extends Actor {
  import Shop._

  val cartActor     = context.actorOf(Props[CartActor], "cartActor")
  var checkoutActor = context.actorOf(Props[Checkout], "checkoutActor")
  private val log   = Logging(context.system, this)



  def receive = LoggingReceive {
    case "init" =>
      cartActor ! CartActor.AddItem(Item("coś"))
      cartActor ! CartActor.AddItem(Item("coś jeszcze"))
      cartActor ! CartActor.RemoveItem(Item("coś"))
      cartActor ! CartActor.StartCheckout
    case CartActor.CheckoutStarted(actor: ActorRef) => {
      checkoutActor ! Checkout.StartCheckout}
    case Checkout.SelectingDeliveryStarted(_) =>
      checkoutActor ! Checkout.SelectDeliveryMethod("fast")
      checkoutActor ! Checkout.SelectPayment("cheap")
      checkoutActor ! Checkout.ReceivePayment
    case Checkout.Uninitialized =>
      cartActor ! CancelCheckout
    case Checkout.CheckOutClosed =>
      log.debug("checkout closed received in shop")
      cartActor ! CartActor.CloseCheckout
    case CartData(cart: Cart) =>
      printCart(cart)
  }
  def printCart(cart: Cart): Unit = {
    log.info("----------  Cart contains: ---------")
    for (item <- cart.items) log.info(item.toString)
  }
}

object ShopApp extends App {
  val system    = ActorSystem("Reactive2")
  val mainActor = system.actorOf(Props[Shop], "shopActor")

  mainActor ! "init"

  Await.result(system.whenTerminated, Duration.Inf)
}
