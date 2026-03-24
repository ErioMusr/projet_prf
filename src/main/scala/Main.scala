import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

object Main {
  def main(args: Array[String]): Unit = {
    println("=== system activated ===\n")

    val rootBehavior = Behaviors.empty[String]
    val system: ActorSystem[String] = ActorSystem(rootBehavior, "OrderManagementSystem")

    try {
      println("[INFO] initiating Actors...")
      val inventoryActor = system.systemActorOf(InventoryActor(), "InventoryActor")
      val paymentActor = system.systemActorOf(PaymentActor(), "PaymentActor")
      val orderActor = system.systemActorOf(
        OrderActor(inventoryActor, paymentActor),
        "OrderActor"
      )
      val httpActor = system.systemActorOf(HttpRequestActor(), "HttpRequestActor")

      println("[SUCCESS] all Actors created\n")

      println("[TEST] sending order...")
      println("-------------------------------------------")

      orderActor ! PlaceOrder("product-1", 2)
      orderActor ! PlaceOrder("product-2", 1)
      orderActor ! PlaceOrder("product-3", 5)

      println("-------------------------------------------\n")

      Thread.sleep(3000)

      println("[INFO] system processed orders, check logs for details\n")
      println("=== review order.txt and payment.txt for detailed log ===\n")

    } catch {
      case e: Exception =>
        println(s"[ERROR] system failed to activate: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      system.terminate()
      println("[INFO] system shutdown")
    }
  }
}

