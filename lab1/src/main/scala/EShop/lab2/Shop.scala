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

  var cartActor     = context.actorOf(Props[CartFSM], "cartActor")

  def receive = LoggingReceive {
    case "initProperTransaction" =>
      cartActor ! CartActor.AddItem(Item("coś"))
      cartActor ! CartActor.AddItem(Item("coś jeszcze"))
      cartActor ! CartActor.RemoveItem(Item("coś"))
      cartActor ! CartActor.StartCheckout

    case "initTimedOutTransaction" =>
      cartActor ! CartActor.AddItem(Item("coś"))
      cartActor ! CartActor.AddItem(Item("coś jeszcze"))
  }
}

object ShopApp extends App {
  val system    = ActorSystem("Reactive2")
  val mainActor = system.actorOf(Props[Shop], "shopActor")

//  mainActor ! "initProperTransaction"
  mainActor ! "initTimedOutTransaction"

  Await.result(system.whenTerminated, Duration.Inf)
}
