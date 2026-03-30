import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.mutable
import scala.math.BigDecimal
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object PaymentActor {
  // Simulated payment gateway parameters
  private val MIN_PAYMENT = 0.01
  private val MAX_PAYMENT = 100000.00
  private val PAYMENT_LIFESPAN_MS = 2 * 60 * 1000  // 2 minutes

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("PaymentActor initialized")
    implicit val ec: ExecutionContext = context.executionContext
    context.system.scheduler.scheduleAtFixedRate(30.seconds, 30.seconds) { () => context.self ! CleanupExpiredPaymentsMsg }
    val initialPayments = FileStore.loadPayments().map { p =>
      p._1 -> (PaymentRecord(p._1, p._2, p._3, p._4, p._5), p._5)
    }.toMap
    paymentBehavior(mutable.Map.from(initialPayments))
  }

  private def paymentBehavior(paymentHistory: mutable.Map[String, (PaymentRecord, Long)]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        // 增加了一个 requiredPrice 参数
        case ProcessPayment(orderId, productId, amount, requiredPrice, replyTo) =>
          context.log.info(s"Processing payment for order: $orderId, amount: $amount, required: $requiredPrice")

          // 核心校验转移到了这里！
          if (amount < requiredPrice) {
            context.log.warn(s"Insufficient funds: $amount for order $orderId (required: $requiredPrice)")
            // 直接返回 PaymentFailed 给 UI
            replyTo ! PaymentFailed(orderId, f"Insufficient funds! You need $$$requiredPrice%.2f, but only provided $$$amount%.2f")
            Behaviors.same
          } else if (amount > MAX_PAYMENT) {
            context.log.warn(s"Payment amount too large: $amount for order $orderId")
            replyTo ! PaymentFailed(orderId, s"Payment amount cannot exceed $MAX_PAYMENT")
            Behaviors.same
          } else {
            // Simulate payment processing
            val paymentSucceeded = simulatePaymentGateway(amount)

            if (paymentSucceeded) {
              context.log.info(s"Payment successful for order: $orderId")
              val record = PaymentRecord(orderId, productId, amount, "SUCCESS", System.currentTimeMillis())
              paymentHistory(orderId) = (record, System.currentTimeMillis())
              FileStore.savePayment(record)
              replyTo ! PaymentSuccessful(orderId, amount)
              paymentBehavior(paymentHistory)
            } else {
              context.log.warn(s"Payment declined for order: $orderId")
              val record = PaymentRecord(orderId, productId, amount, "FAILED", System.currentTimeMillis())
              paymentHistory(orderId) = (record, System.currentTimeMillis())
              FileStore.savePayment(record)
              replyTo ! PaymentFailed(orderId, "Payment declined by payment gateway (Simulated failure)")
              Behaviors.same
            }
          }

        case GetAllPayments(replyTo) =>
          val payments = paymentHistory.map { case (_, (record, _)) =>
            (record.orderId, record.productId, record.amount, record.status, record.timestamp)
          }.toList
          replyTo ! AllPaymentsResponse(payments)
          Behaviors.same

        case CleanupExpiredPaymentsMsg =>
          val now = System.currentTimeMillis()
          val expiredKeys = paymentHistory.filter { case (_, (_, timestamp)) =>
            now - timestamp > PAYMENT_LIFESPAN_MS
          }.keys.toList
          expiredKeys.foreach { key =>
            context.log.info(s"Cleaning up expired payment: $key")
            paymentHistory.remove(key)
          }
          Behaviors.same

        case _ =>
          context.log.warn("Unhandled message in PaymentActor")
          Behaviors.unhandled
      }
    }

  // Simulate payment gateway with 90% success rate
  private def simulatePaymentGateway(amount: Double): Boolean = {
    scala.util.Random.nextDouble() < 0.9
  }
}

// Payment record for persistence
case class PaymentRecord(orderId: String, productId: String, amount: Double, status: String, timestamp: Long)

