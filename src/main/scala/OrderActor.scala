import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors


object OrderActor {
  def apply(inventoryActor: ActorRef[Command]): Behavior[Command] = Behaviors.setup { context =>
    val inventoryResponseAdapter: ActorRef[InventoryResponse] =
      context.messageAdapter(response => InventoryRes(response))

    Behaviors.receiveMessage{
      case PlaceOrder(pid, qty) =>
        FileStore.saveOrder(pid, qty, "INIT")
        inventoryActor ! CheckInventoryAmount(pid, qty, inventoryResponseAdapter)
        Behaviors.same


      case InventoryRes(res) =>
        res match {
          case InventoryAvailable(pid) =>
            context.log.info(s"inventory confirmed available: $pid")
            FileStore.saveOrder(pid, 0, "STOCK_RESERVED")
            Behaviors.same
          case InventoryNotEnough(pid) =>
            context.log.warn(s"inventory not enough: $pid")
            FileStore.saveOrder(pid, 0, "ORDER_FAILED_NO_STOCK")
            Behaviors.same
        }



      case _ => Behaviors.unhandled
    }
  }
}