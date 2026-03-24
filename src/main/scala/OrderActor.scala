import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors


object OrderActor {
  def apply(inventoryActor: ActorRef[Command], paymentActor: ActorRef[Command]): Behavior[Command] = Behaviors.setup { context =>
    val inventoryResponseAdapter: ActorRef[InventoryResponse] =
      context.messageAdapter(response => InventoryRes(response))

    val paymentResponseAdapter: ActorRef[PaymentResponse] =
      context.messageAdapter(response => PaymentRes(response))

    orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter)
  }

  private def orderBehavior(
    inventoryActor: ActorRef[Command],
    paymentActor: ActorRef[Command],
    inventoryResponseAdapter: ActorRef[InventoryResponse],
    paymentResponseAdapter: ActorRef[PaymentResponse]
  ): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case PlaceOrder(productId, quantity) =>
        context.log.info(s"Placing order for product: $productId, quantity: $quantity")
        FileStore.saveOrder(productId, quantity, "INIT")
        inventoryActor ! CheckInventoryAmount(productId, quantity, inventoryResponseAdapter)
        Behaviors.same

      case InventoryRes(res) =>
        res match {
          case InventoryAvailable(productId) =>
            context.log.info(s"inventory confirmed available: $productId")
            FileStore.saveOrder(productId, 0, "STOCK_RESERVED")
            // Proceed with payment (example: orderId = productId, amount = 100.0)
            paymentActor ! ProcessPayment(productId, productId, 100.0, paymentResponseAdapter)
            Behaviors.same
          case InventoryNotEnough(productId) =>
            context.log.warn(s"inventory not enough: $productId")
            FileStore.saveOrder(productId, 0, "ORDER_FAILED_NO_STOCK")
            Behaviors.same
        }

      case PaymentRes(res) =>
        res match {
          case PaymentSuccessful(orderId) =>
            context.log.info(s"payment successful for order: $orderId")
            FileStore.saveOrder(orderId, 0, "PAYMENT_SUCCESSFUL")
            Behaviors.same
          case PaymentFailed(orderId, reason) =>
            context.log.warn(s"payment failed for order: $orderId, reason: $reason")
            FileStore.saveOrder(orderId, 0, "PAYMENT_FAILED")
            Behaviors.same
        }

      case _ => Behaviors.unhandled
    }
  }
}



