import akka.actor.typed.ActorSystem
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {
    println("="*60)
    println("ORDER MANAGEMENT SYSTEM - Console Edition")
    println("="*60)
    println("[INFO] Initializing actor system...\n")

    val rootBehavior = Behaviors.empty[String]
    val system: ActorSystem[String] = ActorSystem(rootBehavior, "OrderManagementSystem")

    var orderActor: akka.actor.typed.ActorRef[Command] = null
    var inventoryActor: akka.actor.typed.ActorRef[Command] = null
    var paymentActor: akka.actor.typed.ActorRef[Command] = null

    try {
      println("[INFO] Creating actors...")
      inventoryActor = system.systemActorOf(
        Behaviors.supervise(InventoryActor()).onFailure(SupervisorStrategy.restart),
        "InventoryActor"
      )
      paymentActor = system.systemActorOf(
        Behaviors.supervise(PaymentActor()).onFailure(SupervisorStrategy.restart),
        "PaymentActor"
      )
      orderActor = system.systemActorOf(
        Behaviors.supervise(OrderActor(inventoryActor, paymentActor)).onFailure(SupervisorStrategy.restart),
        "OrderActor"
      )

      println("[SUCCESS] All actors initialized\n")

      ConsoleUI.startConsole(orderActor, inventoryActor, paymentActor, system)

      println("\n[INFO] Console UI exited. Preparing to shutdown...")
      println("[INFO] Scanning for unpaid orders to release inventory...")

      implicit val timeout: Timeout = 5.seconds
      implicit val scheduler = system.scheduler

      val shutdownFuture = orderActor.ask(ref => CancelAllUnpaidOrders(ref))
      Await.result(shutdownFuture, 5.seconds)
      Thread.sleep(1000)

      println("[SUCCESS] All unpaid orders cancelled and inventory released.")

    } catch {
      case e: Exception =>
        println(s"[ERROR] System error: ${e.getMessage}")
    } finally {
      println("\n[INFO] Shutting down actor system...")
      try {
        system.terminate()
        Await.result(system.whenTerminated, 10.seconds)
      } catch {
        case e: Exception => println(s"[WARNING] Error during shutdown: ${e.getMessage}")
      }
      println("[INFO] System shutdown complete. Goodbye!")
    }
  }
}