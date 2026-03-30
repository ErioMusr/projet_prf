import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import scala.io.StdIn
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.typed.ActorSystem
import akka.actor.typed.Scheduler

object ConsoleUI {
  implicit val timeout: Timeout = 3.seconds

  def startConsole(
    orderActor: ActorRef[Command],
    inventoryActor: ActorRef[Command],
    paymentActor: ActorRef[Command],
    system: ActorSystem[_]
  ): Unit = {
    implicit val scheduler: Scheduler = system.scheduler

    mainLoop(orderActor, inventoryActor, paymentActor, scheduler, running = true)
  }

  private def mainLoop(
    orderActor: ActorRef[Command],
    inventoryActor: ActorRef[Command],
    paymentActor: ActorRef[Command],
    scheduler: Scheduler,
    running: Boolean
  ): Unit = {
    if (!running) return

    println("\n" + "="*60)
    println("ORDER MANAGEMENT SYSTEM")
    println("="*60)
    println("1. User Mode    - Place orders and check status")
    println("2. Admin Mode   - Manage inventory and view all records")
    println("0. Exit")
    println("="*60)
    print("Select option: ")

    val choice = StdIn.readLine().trim

    val continueRunning = choice match {
      case "1" =>
        userMode(orderActor, paymentActor, scheduler)
        true
      case "2" =>
        adminMode(orderActor, inventoryActor, paymentActor, scheduler)
        true
      case "0" =>
        println("\n[INFO] Exiting system...")
        false
      case _ =>
        println("\n[ERROR] Invalid option. Please try again.")
        true
    }

    if (continueRunning) {
      mainLoop(orderActor, inventoryActor, paymentActor, scheduler, running = true)
    }
  }

  private def userMode(
    orderActor: ActorRef[Command],
    paymentActor: ActorRef[Command],
    scheduler: Scheduler
  ): Unit = {
    implicit val implScheduler: Scheduler = scheduler
    var userRunning = true

    while (userRunning) {
      println("\n--- USER MODE ---")
      println("1. Place Order")
      println("2. Pay for Order")
      println("3. Check Order Status")
      println("4. Check Payment Status")
      println("0. Back to Main Menu")
      print("Select option: ")

      val choice = StdIn.readLine().trim

      choice match {
        case "1" =>
          print("Enter product ID (e.g., product-1): ")
          val productId = StdIn.readLine().trim
          print("Enter quantity: ")
          val quantity = try {
            StdIn.readLine().trim.toInt
          } catch {
            case _: Exception =>
              println("[ERROR] Invalid quantity")
              0
          }

          if (productId.nonEmpty && quantity > 0) {
            println(s"\n[INFO] Placing order for $productId, quantity: $quantity")

            try {
              val future: Future[OrderCreatedResponse] = orderActor.ask(ref => PlaceOrder(productId, quantity, ref))
              val result = Await.result(future, 5.seconds)

              result match {
                case OrderCreated(orderId, prod, qty,price) =>
                  println(s"[SUCCESS] Order placed successfully!")
                  println(s"[IMPORTANT] Your Order ID: $orderId")
                  println(f"[IMPORTANT] Total Price: $$$price%.2f")
                  println(s"[INFO] Please save this ID. You will need it to pay for this order.")
                case OrderCreationFailed(reason) =>
                  println(s"[ERROR] Order creation failed: $reason")
                case _ =>
                  println("[ERROR] Failed to create order")
              }
            } catch {
              case e: Exception =>
                println(s"[ERROR] Failed to place order: ${e.getMessage}")
            }
          } else {
            println("[ERROR] Invalid product ID or quantity")
          }

        case "2" =>
          print("Enter order ID to pay for: ")
          var orderId = StdIn.readLine().trim
          if (orderId.nonEmpty && !orderId.startsWith("order-")) {
            orderId = s"order-$orderId"
          }

          if (orderId.nonEmpty && orderId.startsWith("order-")) {
            try {
              val future: Future[OrderStatusResponse] = orderActor.ask(ref => GetOrderStatus(orderId, ref))
              val result = Await.result(future, 3.seconds)

              result match {
                case OrderStatus(id, product, qty, price,amountpaid, status, timestamp) =>
                  println(s"\n[ORDER INFO]")
                  println(s"  ID: $id")
                  println(f"  Total Price: $$$price%.2f")
                  println(s"  Current Status: $status")

                  if (status == "STOCK_RESERVED") {
                    println(s"\n[INFO] Processing payment for order: $orderId")
                    println(f"\n[INFO] You need to pay $$$price%.2f for this order.")
                    print("Enter payment amount: ")

                    val inputAmount = try { StdIn.readLine().trim.toDouble } catch { case _: Exception => 0.0 }

                    // 【修复核心点】确保 ask 内部是 ref => PayForOrder(..., ref)
                    val paymentFuture: Future[PaymentResponse] = orderActor.ask(ref =>
                      PayForOrder(orderId, inputAmount, ref)
                    )

                    val paymentResult = Await.result(paymentFuture, 5.seconds)

                    paymentResult match {
                      case PaymentSuccessful(_,_) =>
                        println("[SUCCESS] Payment processed successfully!")
                      case PaymentFailed(_, reason) =>
                        println(s"[ERROR] Payment failed: $reason")
                      case _ =>
                        println("[ERROR] Unknown payment result")
                    }

                  } else if (status == "INIT" || status == "PENDING_INVENTORY_CHECK") {
                    println("[ERROR] Order is not ready for payment yet (inventory check pending)")
                  } else if (status.contains("PAYMENT")) {
                    println(s"[INFO] This order already has payment status: $status")
                  } else {
                    println(s"[ERROR] Cannot pay for order with status: $status")
                  }

                case OrderNotFound(_) =>
                  println(s"\n[ERROR] Order $orderId not found")
              }
            } catch {
              case e: Exception =>
                println(s"\n[ERROR] Failed to process payment: ${e.getMessage}")
            }
          } else {
            println("[ERROR] Invalid order ID format. Expected: order-TIMESTAMP or just TIMESTAMP")
          }

        case "3" =>
          print("Enter order ID: ")
          var orderId = StdIn.readLine().trim
          if (orderId.nonEmpty && !orderId.startsWith("order-")) {
            orderId = s"order-$orderId"
          }
          if (orderId.nonEmpty) {
            try {
              val future: Future[OrderStatusResponse] = orderActor.ask(ref => GetOrderStatus(orderId, ref))
              val result = Await.result(future, 3.seconds)

              result match {
                case OrderStatus(id, product, qty,price,amountpaid, status, timestamp) =>
                  println(s"\n[ORDER FOUND]")
                  println(s"  ID: $id")
                  println(s"  Product: $product")
                  println(s"  Quantity: $qty")
                  println(s"  Status: $status")
                case OrderNotFound(_) =>
                  println(s"\n[ERROR] Order $orderId not found")
              }
            } catch {
              case e: Exception =>
                println(s"\n[ERROR] Failed to query order: ${e.getMessage}")
            }
          } else {
            println("[ERROR] Invalid order ID")
          }

        case "4" =>
          try {
            val future: Future[AllPaymentsResponse] = paymentActor.ask(ref => GetAllPayments(ref))
            val result = Await.result(future, 3.seconds)

            if (result.payments.nonEmpty) {
              println(s"\n[PAYMENT RECORDS]")
              result.payments.foreach { case (orderId, productId, amount, status, timestamp) =>
                println(f"  Order: $orderId | Product: $productId | Amount: $amount%.2f | Status: $status")
              }
            } else {
              println("\n[INFO] No payments found")
            }
          } catch {
            case e: Exception =>
              println(s"\n[ERROR] Failed to query payments: ${e.getMessage}")
          }

        case "0" =>
          println("\n[INFO] Returning to main menu...")
          userRunning = false

        case _ => println("\n[ERROR] Invalid option. Please try again.")
      }
    }
  }

  private def adminMode(
    orderActor: ActorRef[Command],
    inventoryActor: ActorRef[Command],
    paymentActor: ActorRef[Command],
    scheduler: Scheduler
  ): Unit = {
    implicit val implScheduler: Scheduler = scheduler
    var adminRunning = true

    while (adminRunning) {
      println("\n--- ADMIN MODE ---")
      println("1. View Inventory")
      println("2. Update Inventory")
      println("3. View All Orders")
      println("4. View All Payments")
      println("0. Back to Main Menu")
      print("Select option: ")

      val choice = StdIn.readLine().trim

      choice match {
        case "1" =>
          try {
            val future: Future[InventoryQueryResponse] = inventoryActor.ask(ref => GetInventory(ref))
            val result = Await.result(future, 3.seconds)

            result match {
              case InventoryQueryResult(items) =>
                println(s"\n[INVENTORY]")
                if (items.nonEmpty) {
                  items.foreach { case (productId, qty) =>
                    println(s"  $productId: $qty units")
                  }
                } else {
                  println("  (empty)")
                }
              case _ =>
                println("[ERROR] Failed to query inventory")
            }
          } catch {
            case e: Exception =>
              println(s"[ERROR] Failed to query inventory: ${e.getMessage}")
          }

        case "2" =>
          print("Enter product ID: ")
          val productId = StdIn.readLine().trim
          print("Enter new quantity: ")
          val newQty = try {
            StdIn.readLine().trim.toInt
          } catch {
            case _: Exception =>
              println("[ERROR] Invalid quantity")
              -1
          }

          if (productId.nonEmpty && newQty >= 0) {
            try {
              val future: Future[InventoryUpdateResponse] = inventoryActor.ask(ref => UpdateInventory(productId, newQty, ref))
              val result = Await.result(future, 3.seconds)

              result match {
                case InventoryUpdateSuccess(msg) =>
                  println(s"\n[SUCCESS] $msg")
                case InventoryUpdateFailed(msg) =>
                  println(s"\n[ERROR] $msg")
              }
            } catch {
              case e: Exception =>
                println(s"\n[ERROR] Failed to update inventory: ${e.getMessage}")
            }
          } else {
            println("[ERROR] Invalid product ID or quantity")
          }

        case "3" =>
          try {
            val future: Future[AllOrdersResponse] = orderActor.ask(ref => GetAllOrders(ref))
            val result = Await.result(future, 3.seconds)

            if (result.orders.nonEmpty) {
              println(s"\n[ALL ORDERS]")
              result.orders.foreach { case (orderId, productId, qty,price,amountpaid, status, timestamp) =>
                println(f"  $orderId | Product: $productId | Qty: $qty | Price: $$$price%.2f | Status: $status")
              }
            } else {
              println("\n[INFO] No orders found")
            }
          } catch {
            case e: Exception =>
              println(s"\n[ERROR] Failed to query orders: ${e.getMessage}")
          }

        case "4" =>
          try {
            val future: Future[AllPaymentsResponse] = paymentActor.ask(ref => GetAllPayments(ref))
            val result = Await.result(future, 3.seconds)

            if (result.payments.nonEmpty) {
              println(s"\n[ALL PAYMENTS]")
              result.payments.foreach { case (orderId, productId, amount, status, timestamp) =>
                println(f"  Order: $orderId | Product: $productId | Amount: $amount%.2f | Status: $status")
              }
            } else {
              println("\n[INFO] No payments found")
            }
          } catch {
            case e: Exception =>
              println(s"\n[ERROR] Failed to query payments: ${e.getMessage}")
          }

        case "0" =>
          println("\n[INFO] Returning to main menu...")
          adminRunning = false

        case _ => println("\n[ERROR] Invalid option. Please try again.")
      }
    }
  }
}













