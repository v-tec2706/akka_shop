package EShop.lab3

import EShop.lab2.CartActor.{GetItems, StartCheckout}
import EShop.lab2.{Cart, CartActor, Checkout}
import EShop.lab3.OrderManager.{AddItem, RemoveItem}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val cartExpect = Cart.empty
    cartExpect.addItem("item")
    val cart = system.actorOf(CartActor.props)
    cart ! AddItem("item")
    cart ! GetItems
    expectMsg(cartExpect)
  }

  it should "be empty after adding and removing the same item" in {
    val cart = system.actorOf(CartActor.props)
    cart ! AddItem("item")
    cart ! RemoveItem("item")
    cart ! GetItems
    expectMsg(Cart.empty)
  }

  it should "start checkout" in {
    val cart = system.actorOf(CartActor.props)
    val checkoutActor = system.actorOf(Props(new Checkout(cart)), "checkout")
    cart ! AddItem("item")
    cart ! StartCheckout
    expectNoMessage()
  }
}
