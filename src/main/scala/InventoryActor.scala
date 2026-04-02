import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object InventoryActor {
  def apply(): Behavior[Command] = Behaviors.setup { context =>
    val savedItems = FileStore.loadInventory()
    context.log.info(s"Loaded inventory from file: $savedItems")
    inventory(savedItems)
  }

  private def inventory(items: Map[String, Int]): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case RestoreInventory(productId, quantity) =>
        val currentStock = items.getOrElse(productId, 0)
        val newStock = currentStock + quantity
        val updatedItems = items + (productId -> newStock)
        FileStore.saveInventory(updatedItems)
        context.log.info(s"Restored $quantity units of $productId. New stock: $newStock")
        inventory(updatedItems)

      case CheckInventoryAmount(orderId,productId, quantity, replyTo) =>
        FileStore.logEvent(s"Check_Inv_Start|$orderId|$productId|$quantity")
        if (scala.util.Random.nextDouble() < 0.5) {
          // Simulate communication failure
          val failureType = scala.util.Random.nextInt(2)
          if (failureType == 0) {
            FileStore.logEvent(s"Inv_NotFound|$orderId|$productId")
            replyTo ! ItemNotFound(orderId,productId)
          } else {
            FileStore.logEvent(s"Inv_Insufficient|$orderId|$productId")
            replyTo ! InventoryNotEnough(orderId,productId)
          }
          Behaviors.same
        } else {
          if (!items.contains(productId)) {
            FileStore.logEvent(s"Inv_NotFound|$orderId|$productId")
            replyTo ! ItemNotFound(orderId,productId)
            Behaviors.same
          } else {
            val currentStock = items(productId)
            if (currentStock >= quantity) {
              val newStock = currentStock - quantity
              val updatedItems = items + (productId -> newStock)
              FileStore.saveInventory(updatedItems)
              FileStore.logEvent(s"Inv_Enough_Reduce_Success|$orderId|$productId|$quantity|$newStock")
              replyTo ! InventoryAvailable(orderId,productId)
              inventory(updatedItems)
            } else {
              FileStore.logEvent(s"Inv_Insufficient|$orderId|$productId|$currentStock|$quantity")
              replyTo ! InventoryNotEnough(orderId,productId)
              Behaviors.same
            }
          }
        }

      case GetInventory(replyTo) =>
        replyTo ! InventoryQueryResult(items)
        Behaviors.same

      case UpdateInventory(productId, newQuantity, replyTo) =>
        val updatedItems = items + (productId -> newQuantity)
        FileStore.saveInventory(updatedItems)
        context.log.info(s"Updated inventory for $productId: old=${items.getOrElse(productId, 0)}, new=$newQuantity")
        replyTo ! InventoryUpdateSuccess(s"Inventory updated for $productId to $newQuantity")
        inventory(updatedItems)

      case _ =>
        Behaviors.unhandled
    }
  }
}
