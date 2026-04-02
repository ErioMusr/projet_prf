import akka.actor.typed.ActorRef

case class ShutdownComplete()
// ===== Base Traits =====
sealed trait Command

// ===== Order Messages =====
case class PlaceOrder(productId: String, amount: Int, replyTo: ActorRef[OrderCreatedResponse] = null.asInstanceOf[ActorRef[OrderCreatedResponse]]) extends Command
case class InventoryRes(res: InventoryResponse) extends Command
case class PaymentRes(res: PaymentResponse) extends Command
case class CancelAllUnpaidOrders(replyTo: ActorRef[ShutdownComplete]) extends Command

// Order creation response
sealed trait OrderCreatedResponse
case class OrderCreated(orderId: String, productId: String, quantity: Int, price: Double) extends OrderCreatedResponse
case class OrderCreationFailed(reason: String) extends OrderCreatedResponse

// ===== Inventory Messages =====
case class CheckInventoryAmount(orderId: String, productId: String, quantity: Int, replyTo: ActorRef[InventoryResponse]) extends Command
case class RestoreInventory(productId: String, quantity: Int) extends Command
sealed trait InventoryResponse
case class InventoryAvailable(orderId: String, productId: String) extends InventoryResponse
case class InventoryNotEnough(orderId: String, productId: String) extends InventoryResponse
case class ItemNotFound(orderId: String, productId: String) extends InventoryResponse

// ===== Payment Messages =====
case class ProcessPayment(orderId: String, productId: String, amount: Double, requiredPrice: Double, replyTo: ActorRef[PaymentResponse]) extends Command
case class PayForOrder(orderId: String, amount: Double, replyTo: ActorRef[PaymentResponse]) extends Command
sealed trait PaymentResponse
case class PaymentSuccessful(orderId: String, amountPaid: Double) extends PaymentResponse
case class PaymentFailed(orderId: String, reason: String) extends PaymentResponse

// ===== Query Messages for Admin Mode =====
case class GetOrderStatus(orderId: String, replyTo: ActorRef[OrderStatusResponse]) extends Command
case class GetAllOrders(replyTo: ActorRef[AllOrdersResponse]) extends Command
case class GetPaymentStatus(orderId: String, replyTo: ActorRef[PaymentStatusResponse]) extends Command
case class GetAllPayments(replyTo: ActorRef[AllPaymentsResponse]) extends Command
case class GetInventory(replyTo: ActorRef[InventoryQueryResponse]) extends Command
case class UpdateInventory(productId: String, newQuantity: Int, replyTo: ActorRef[InventoryUpdateResponse]) extends Command

// ===== Internal Cleanup Messages =====
case object CleanupExpiredOrdersMsg extends Command
case object CleanupExpiredPaymentsMsg extends Command
case class InventoryCheckTimeout(orderId: String) extends Command
case class PaymentTimeout(orderId: String) extends Command

sealed trait OrderStatusResponse
case class OrderStatus(orderId: String, productId: String, quantity: Int, price: Double, paidAmount: Double, status: String, timestamp: Long) extends OrderStatusResponse
case class OrderNotFound(orderId: String) extends OrderStatusResponse

case class AllOrdersResponse(orders: List[(String, String, Int, Double,Double, String, Long)])

sealed trait PaymentStatusResponse
case class PaymentStatus(orderId: String, productId: String, amount: Double, status: String, timestamp: Long) extends PaymentStatusResponse
case class PaymentNotFound(orderId: String) extends PaymentStatusResponse

case class AllPaymentsResponse(payments: List[(String, String, Double, String, Long)])

sealed trait InventoryQueryResponse
case class InventoryQueryResult(items: Map[String, Int]) extends InventoryQueryResponse

sealed trait InventoryUpdateResponse
case class InventoryUpdateSuccess(message: String) extends InventoryUpdateResponse
case class InventoryUpdateFailed(message: String) extends InventoryUpdateResponse
