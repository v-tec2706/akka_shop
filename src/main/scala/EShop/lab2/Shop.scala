package EShop.lab2

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Shop {
  case class Item(itemName: String)
}

class Shop extends Actor {
  import Shop._

  private val log = Logging(context.system, this)

  var cartActor               = context.actorOf(Props[CartFSM], "cartActor")
  var persistentCartActor     = context.actorOf(Props(new PersistentCartActor("1233")))
  var persistentCheckoutActor = context.actorOf(Props(new PersistentCheckout(cartActor, "12345")))
  def receive = LoggingReceive {
    case "initProperTransaction" =>
      cartActor ! CartActor.AddItem(Item("coś"))
      cartActor ! CartActor.AddItem(Item("coś jeszcze"))
      cartActor ! CartActor.RemoveItem(Item("coś"))
      cartActor ! CartActor.StartCheckout

    case "initTimedOutTransaction" =>
      cartActor ! CartActor.AddItem(Item("coś"))
      cartActor ! CartActor.AddItem(Item("coś jeszcze"))

    case "initPersistent" =>
      persistentCartActor ! AddItem("coś")
      persistentCartActor ! AddItem("coś jeszcze")
      persistentCartActor ! RemoveItem("coś")
      persistentCartActor ! RemoveItem("coś jeszcze")

    case "checkout" =>
      persistentCheckoutActor ! StartCheckout
      persistentCheckoutActor ! SelectDeliveryMethod("DHL")
      persistentCheckoutActor ! SelectPayment("PayPal")
      context.stop(persistentCheckoutActor)
      persistentCheckoutActor = context.actorOf(Props(new PersistentCheckout(cartActor, "12345")))
      persistentCheckoutActor ! ReceivePayment
      persistentCheckoutActor ! "checkMessage"
  }
}

object ShopApp extends App {
  val system    = ActorSystem("Reactive2")
  val mainActor = system.actorOf(Props[Shop], "shopActor")

//  mainActor ! "initProperTransaction"
//  mainActor ! "initTimedOutTransaction"
  mainActor ! "checkout"

  Await.result(system.whenTerminated, Duration.Inf)
}
