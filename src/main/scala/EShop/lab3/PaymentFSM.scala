package EShop.lab3

import EShop.lab2.Checkout
import EShop.lab3.Payment._
import akka.actor.{ActorRef, FSM, Props}

object PaymentFSM {
  def props(method: String, orderManager: ActorRef, checkout: ActorRef) =
    Props(new PaymentFSM(method, orderManager, checkout))

}

class PaymentFSM(
  method: String,
  orderManager: ActorRef,
  checkout: ActorRef
) extends FSM[State, Data] {

  startWith(WaitingForPayment, Empty)

  when(WaitingForPayment) {
    case Event(DoPayment, _) =>
      sender ! PaymentConfirmed
      checkout ! Checkout.ReceivePayment
      stay()
  }

  //  whenUnhandled {
  //    case Event(e, s) =>
  //      log.warning("received unexpected request {} in state {}/{}", e, stateName, s)
  //      stay
  //  }
}
