import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext

object OrderActor {
  private val ORDER_LIFESPAN_MS = 5 * 60 * 1000  // 5 minutes
  private val PAYMENT_TIMEOUT = 3.seconds

  def apply(inventoryActor: ActorRef[Command], paymentActor: ActorRef[Command]): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("OrderActor initialized with 5-minute lifecycle")

    implicit val ec: ExecutionContext = context.executionContext

    val allOrdersFromFile = FileStore.loadOrders()
    val now = System.currentTimeMillis()
    val recoveredOrders = allOrdersFromFile.map {
      case (id, pid, qty, price, paid, status, ts) =>
        if (status == "STOCK_RESERVED" || status == "PENDING_INVENTORY_CHECK") {
          context.log.info(s"[RECOVERY] Found unfinished order $id. Rolling back inventory for $pid ($qty units).")
          inventoryActor ! RestoreInventory(pid, qty)
          val failStatus = "PAYMENT_FAILED_DUE_TO_SYSTEM_RESTART"
          FileStore.saveOrder(id, pid, qty, price, paid, failStatus)
          id -> (pid, qty, price, paid, failStatus, ts)
        } else {
          id -> (pid, qty, price, paid, status, ts)
        }
    }.toMap
    // Schedule cleanup every 1 minute
    context.system.scheduler.scheduleAtFixedRate(1.minute, 1.minute) { () =>
      context.self ! CleanupExpiredOrdersMsg
    }

    val inventoryResponseAdapter: ActorRef[InventoryResponse] =
      context.messageAdapter(response => InventoryRes(response))

    val initialOrders = FileStore.loadOrders().map { o =>
      o._1 -> (o._2, o._3, o._4, o._5, o._6, o._7) // productId, qty, price, paidAmount, status, ts
    }.toMap


    val paymentResponseAdapter: ActorRef[PaymentResponse] =
      context.messageAdapter(response => PaymentRes(response))

    orderBehavior(
      inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter,
      mutable.Map.from(initialOrders),
      mutable.Map[String, ActorRef[OrderCreatedResponse]](),
      mutable.Map[String, ActorRef[PaymentResponse]]()
    )
  }

  private def orderBehavior(
    inventoryActor: ActorRef[Command],
    paymentActor: ActorRef[Command],
    inventoryResponseAdapter: ActorRef[InventoryResponse],
    paymentResponseAdapter: ActorRef[PaymentResponse],
    orderRecords: mutable.Map[String, (String, Int, Double, Double,String, Long)],
    pendingReplies: mutable.Map[String, ActorRef[OrderCreatedResponse]],
    pendingPaymentReplies: mutable.Map[String, ActorRef[PaymentResponse]]
  ): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case PlaceOrder(productId, quantity, replyTo) =>
        FileStore.logEvent(s"PlaceOrder|$productId|$quantity")
        val orderId = s"order-${System.currentTimeMillis()}"
        context.log.info(s"Placing order $orderId for product: $productId, quantity: $quantity")
        val unitPrice = 10.0 + scala.util.Random.nextDouble() * 10.0
        val totalPrice = BigDecimal(quantity * unitPrice).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
        // Store with status PENDING_INVENTORY_CHECK - will be kept only if inventory is sufficient
        orderRecords(orderId) = (productId, quantity, totalPrice,0.0, "PENDING_INVENTORY_CHECK", System.currentTimeMillis())

        // Store the reply channel to send response after inventory check
        if (replyTo != null) {
          pendingReplies(orderId) = replyTo
        }

        FileStore.saveOrder(orderId, productId, quantity,totalPrice,0.0, "PENDING_INVENTORY_CHECK")
        inventoryActor ! CheckInventoryAmount(orderId, productId, quantity, inventoryResponseAdapter)
        // Schedule timeout for inventory check
        context.scheduleOnce(5.seconds, context.self, InventoryCheckTimeout(orderId))
        orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

      case InventoryRes(res) =>
        res match {
          case ItemNotFound(orderId, productId) =>
            orderRecords.remove(orderId)
            if (pendingReplies.contains(orderId)) {
              pendingReplies(orderId) ! OrderCreationFailed(s"Product '$productId' does not exist in inventory.")
              pendingReplies.remove(orderId)
            }
            orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

          case InventoryAvailable(orderId, productId) =>
            context.log.info(s"Inventory confirmed available: $productId for order $orderId")
            if (orderRecords.contains(orderId)) {
              val (pid, qty, price, amountpaid, _, ts) = orderRecords(orderId)
              orderRecords(orderId) = (pid, qty, price, amountpaid, "STOCK_RESERVED", ts)
              FileStore.saveOrder(orderId, pid, qty, price, amountpaid, "STOCK_RESERVED")

              if (pendingReplies.contains(orderId)) {
                pendingReplies(orderId) ! OrderCreated(orderId, pid, qty, price)
                pendingReplies.remove(orderId)
              }
            }
            orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

          case InventoryNotEnough(orderId, productId) =>
            context.log.warn(s"Inventory not enough: $productId for order $orderId")
            if (orderRecords.contains(orderId)) {
              orderRecords.remove(orderId)
              if (pendingReplies.contains(orderId)) {
                pendingReplies(orderId) ! OrderCreationFailed(s"Insufficient inventory for product $productId. Required quantity exceeds available stock.")
                pendingReplies.remove(orderId)
              }
            }
            orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)
        }

      case CancelAllUnpaidOrders(replyTo) =>
        context.log.info("Cancelling all unpaid orders and restoring inventory...")
        orderRecords.foreach { case (orderId, (productId, qty, price, amountpaid, status, ts)) =>
          if (status == "STOCK_RESERVED") {
            inventoryActor ! RestoreInventory(productId, qty)
            orderRecords(orderId) = (productId, qty, price, amountpaid, "PAYMENT_FAILED", ts)
            FileStore.saveOrder(orderId, productId, qty, price, amountpaid, "PAYMENT_FAILED")
            context.log.info(s"Order $orderId cancelled, $qty units of $productId restored.")

          } else if (status == "PENDING_INVENTORY_CHECK") {
            orderRecords(orderId) = (productId, qty, price, amountpaid, "PAYMENT_FAILED", ts)
            FileStore.saveOrder(orderId, productId, qty, price, amountpaid, "PAYMENT_FAILED")
          }
        }
        replyTo ! ShutdownComplete()
        Behaviors.same

      case PaymentRes(res) =>
        res match {
          case PaymentSuccessful(orderId,amountPaid) =>
            context.log.info(s"Payment successful for order: $orderId")
            if (orderRecords.contains(orderId)) {
              val (productId, qty, price,amountpaid, _, ts) = orderRecords(orderId)
              orderRecords(orderId) = (productId, qty, price,amountpaid, "PAYMENT_SUCCESSFUL", ts)
              FileStore.saveOrder(orderId, productId, qty, price,amountpaid, "PAYMENT_SUCCESSFUL")

              // Reply to pending payment request
              if (pendingPaymentReplies.contains(orderId)) {
                context.log.info(s"Replying to payment ask for order: $orderId")
                pendingPaymentReplies(orderId) ! PaymentSuccessful(orderId,amountPaid)
                pendingPaymentReplies.remove(orderId)
              }
            }
            orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

          case PaymentFailed(orderId, reason) =>
            context.log.warn(s"Payment failed for order: $orderId, reason: $reason")
            if (orderRecords.contains(orderId)) {
              val (productId, qty, price,amountpaid, _, ts) = orderRecords(orderId)
              orderRecords(orderId) = (productId, qty, price,amountpaid, "PAYMENT_FAILED", ts)
              FileStore.saveOrder(orderId, productId, qty, price,amountpaid, "PAYMENT_FAILED")

              // Reply to pending payment request
              if (pendingPaymentReplies.contains(orderId)) {
                context.log.info(s"Replying to payment ask failure for order: $orderId")
                pendingPaymentReplies(orderId) ! PaymentFailed(orderId, reason)
                pendingPaymentReplies.remove(orderId)
              }
            }
            orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)
        }

      case PayForOrder(orderId, amount, replyTo) =>
        orderRecords.get(orderId) match {
          case Some((productId, _, price,_, _, _)) =>
              pendingPaymentReplies(orderId) = replyTo
            paymentActor ! ProcessPayment(orderId, productId, amount, price, paymentResponseAdapter)
            // Schedule timeout for payment
            context.scheduleOnce(3.seconds, context.self, PaymentTimeout(orderId))
          case None =>
            replyTo ! PaymentFailed(orderId, "Order not found")
        }
        orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

      case GetAllOrders(replyTo) =>
        val orders = orderRecords.map { case (orderId, (productId, qty, price,amountpaid, status, timestamp)) =>
          (orderId, productId, qty, price,amountpaid, status, timestamp)
        }.toList
        replyTo ! AllOrdersResponse(orders)
        orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)


      case GetOrderStatus(orderId, replyTo) =>
        orderRecords.get(orderId) match {
          case Some((productId, qty, price,amountpaid, status, timestamp)) =>
            replyTo ! OrderStatus(orderId, productId, qty, price,amountpaid, status, timestamp)
          case None =>
            replyTo ! OrderNotFound(orderId)
        }
        orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)


      case CleanupExpiredOrdersMsg =>
        val now = System.currentTimeMillis()
        val expiredKeys = orderRecords.filter { case (_, (_, _, _,_,_, timestamp)) =>
          now - timestamp > ORDER_LIFESPAN_MS
        }.keys.toList
        expiredKeys.foreach { key =>
          context.log.info(s"Cleaning up expired order: $key")
          // Cancel unpaid orders and restore inventory
          orderRecords.get(key).foreach { case (productId, qty,price,amountpaid, status, _) =>
            if (status == "STOCK_RESERVED" || status == "PENDING_INVENTORY_CHECK") {
              inventoryActor ! RestoreInventory(productId, qty)
              context.log.info(s"Order $key expired - cancelling and restoring inventory")
            }
          }
          orderRecords.remove(key)
          pendingReplies.remove(key)
          pendingPaymentReplies.remove(key)
        }
        orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

      case InventoryCheckTimeout(orderId) =>
        if (orderRecords.contains(orderId) && orderRecords(orderId)._5 == "PENDING_INVENTORY_CHECK") {
          FileStore.logEvent(s"Check_Inv_Timeout|$orderId")
          orderRecords.remove(orderId)
          if (pendingReplies.contains(orderId)) {
            pendingReplies(orderId) ! OrderCreationFailed("Inventory check timed out.")
            pendingReplies.remove(orderId)
          }
        }
        orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

      case PaymentTimeout(orderId) =>
        if (pendingPaymentReplies.contains(orderId)) {
          FileStore.logEvent(s"Payment_Timeout|$orderId")
          pendingPaymentReplies(orderId) ! PaymentFailed(orderId, "Payment processing timed out.")
          pendingPaymentReplies.remove(orderId)
          inventoryActor ! RestoreInventory(productId, qty)
        }
        orderBehavior(inventoryActor, paymentActor, inventoryResponseAdapter, paymentResponseAdapter, orderRecords, pendingReplies, pendingPaymentReplies)

      case _ => Behaviors.unhandled
    }
  }
}
