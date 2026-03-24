import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.mutable
import scala.math.BigDecimal

object PaymentActor {
  // Hardcoded payment accounts (simulated payment gateway)
  private val MIN_PAYMENT = 0.01
  private val MAX_PAYMENT = 100000.00

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("PaymentActor initialized")
    paymentBehavior(mutable.Map[String, PaymentRecord]())
  }

  private def paymentBehavior(paymentHistory: mutable.Map[String, PaymentRecord]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case ProcessPayment(orderId, productId, amount, replyTo) =>
          context.log.info(s"Processing payment for order: $orderId, amount: $amount")

          // Validate payment amount
          if (amount < MIN_PAYMENT) {
            context.log.warn(s"Payment amount too small: $amount for order $orderId")
            replyTo ! PaymentFailed(orderId, s"Payment amount must be at least $MIN_PAYMENT")
            Behaviors.same
          } else if (amount > MAX_PAYMENT) {
            context.log.warn(s"Payment amount too large: $amount for order $orderId")
            replyTo ! PaymentFailed(orderId, s"Payment amount cannot exceed $MAX_PAYMENT")
            Behaviors.same
          } else {
            // Simulate payment processing (in real scenario, call external payment gateway)
            val paymentSucceeded = simulatePaymentGateway(amount)

            if (paymentSucceeded) {
              context.log.info(s"Payment successful for order: $orderId")
              val record = PaymentRecord(orderId, productId, amount, "SUCCESS", System.currentTimeMillis())
              paymentHistory(orderId) = record
              FileStore.savePayment(record)
              replyTo ! PaymentSuccessful(orderId)
              paymentBehavior(paymentHistory)
            } else {
              context.log.warn(s"Payment declined for order: $orderId")
              val record = PaymentRecord(orderId, productId, amount, "FAILED", System.currentTimeMillis())
              paymentHistory(orderId) = record
              FileStore.savePayment(record)
              replyTo ! PaymentFailed(orderId, "Payment declined by payment gateway")
              Behaviors.same
            }
          }

        case _ =>
          context.log.warn("Unhandled message in PaymentActor")
          Behaviors.unhandled
      }
    }

  // Simulate a payment gateway with 90% success rate
  private def simulatePaymentGateway(amount: Double): Boolean = {
    scala.util.Random.nextDouble() < 0.9
  }
}

// Payment record for persistence
case class PaymentRecord(orderId: String, productId: String, amount: Double, status: String, timestamp: Long)

