import akka.actor.typed.ActorRef

sealed trait Command

case class PlaceOrder(productId: String, amount: Int) extends Command
case class PaymentResult(success: Boolean) extends Command
case class InventoryResult(success: Boolean) extends Command

case class InventoryRes(res: InventoryResponse) extends Command

case class ProcessPayment(orderId: String, price: Double) extends Command

case class CheckInventoryAmount(productId: String, quantity: Int ,replyTo: ActorRef[InventoryResponse]) extends Command

sealed trait InventoryResponse
case class InventoryAvailable(productId: String) extends InventoryResponse
case class InventoryNotEnough(productId: String) extends InventoryResponse