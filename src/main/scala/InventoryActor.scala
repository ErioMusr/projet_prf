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
      case CheckInventoryAmount(productId, quantity,replyTo) =>
        val currentStock = items.getOrElse(productId, 0)
        if (currentStock >= quantity) {
          val newStock = currentStock - quantity
          val updatedItems = items + (productId -> newStock)
          FileStore.saveInventory(updatedItems)
          replyTo ! InventoryAvailable(productId)
          context.log.info(s"inventory sufficient: $productId (we have: $currentStock, required: $quantity)")
          inventory(updatedItems)
        } else {
          context.log.warn(s"inventory insufficient: $productId (we have: $currentStock, required: $quantity)")
          replyTo ! InventoryNotEnough(productId)
          Behaviors.same
        }

      case _ =>
        Behaviors.unhandled
    }
  }
}
