import akka.actor.typed.ActorRef

// ===== Base Traits =====
sealed trait Command

// ===== Order Messages =====
case class PlaceOrder(productId: String, amount: Int) extends Command
case class InventoryRes(res: InventoryResponse) extends Command
case class PaymentRes(res: PaymentResponse) extends Command

// ===== Inventory Messages =====
case class CheckInventoryAmount(productId: String, quantity: Int, replyTo: ActorRef[InventoryResponse]) extends Command

sealed trait InventoryResponse
case class InventoryAvailable(productId: String) extends InventoryResponse
case class InventoryNotEnough(productId: String) extends InventoryResponse

// ===== Payment Messages =====
case class ProcessPayment(orderId: String, productId: String, amount: Double, replyTo: ActorRef[PaymentResponse]) extends Command

sealed trait PaymentResponse
case class PaymentSuccessful(orderId: String) extends PaymentResponse
case class PaymentFailed(orderId: String, reason: String) extends PaymentResponse

// ===== HTTP Request Messages (for future HttpRequestActor) =====
case class SendHttpRequest(orderId: String, endpoint: String, data: String) extends Command

sealed trait HttpResponse
case class HttpSuccess(orderId: String, response: String) extends HttpResponse
case class HttpError(orderId: String, error: String) extends HttpResponse
