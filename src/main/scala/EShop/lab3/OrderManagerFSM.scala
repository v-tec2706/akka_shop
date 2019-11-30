package EShop.lab3

import EShop.lab2.CartActor.{CheckoutStarted, StartCheckout}
import EShop.lab2.{CartActor, CartFSM, Checkout}
import EShop.lab3.OrderManager._
import akka.actor.{ActorRef, FSM, Props}

class OrderManagerFSM extends FSM[State, Data] {

  startWith(Uninitialized, Empty)

  when(Uninitialized) {
    case Event(AddItem(item), _) =>
      val cartActor = context.actorOf(Props[CartFSM], "cartActor")
      cartActor ! CartActor.AddItem(item)
      sender ! Done
      goto(Open).using(CartData(cartActor))
  }

  when(Open) {
    case Event(AddItem(item), cartActor: CartData) =>
      cartActor.cartRef ! AddItem(item)
      sender ! Done
      stay().using(cartActor)
    case Event(RemoveItem(item), cartActor: CartData) =>
      cartActor.cartRef ! RemoveItem(item)
      sender ! Done
      stay().using(cartActor)

    case Event(Buy, cartActor: CartData) =>
      cartActor.cartRef ! StartCheckout
      goto(InCheckout).using(CartDataWithSender(cartActor.cartRef, sender))
  }

  when(InCheckout) {
    case Event(CheckoutStarted(checkoutRef: ActorRef, cart), cartDataWithSender: CartDataWithSender) =>
      checkoutRef ! Checkout.StartCheckout
      cartDataWithSender.sender ! Done
      stay().using(InCheckoutData(checkoutRef))
    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), inCheckoutData: InCheckoutData) =>
      inCheckoutData.checkoutRef ! Checkout.SelectDeliveryMethod(delivery)
      inCheckoutData.checkoutRef ! Checkout.SelectPayment(payment)
      goto(InPayment).using(InPaymentData(sender))
    case m =>
      log.info("hereeeeee" + m.event + m.stateData)
      stay()
  }

  when(InPayment) {
    case Event(Checkout.PaymentStarted(paymentRef), inPaymentData: InPaymentData) =>
      inPaymentData.paymentRef ! Done
      stay.using(InPaymentDataWithSender(paymentRef, inPaymentData.paymentRef))
    case Event(Pay, inPaymentDataWithSender: InPaymentDataWithSender) =>
      inPaymentDataWithSender.paymentRef ! Payment.DoPayment
      sender ! Done
      goto(Finished)
    case Event(Payment.PaymentConfirmed, _) => goto(Finished)
  }

  when(Finished) {
    case _ =>
      sender ! "order manager finished job"
      stay()
  }

}
