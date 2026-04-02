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
        case ProcessPayment(orderId, productId, amount, requiredPrice, replyTo) =>
          FileStore.logEvent(s"Submit_Payment|$orderId|$productId|$amount|$requiredPrice")
          if (scala.util.Random.nextDouble() < 0.5) {
            FileStore.logEvent(s"Payment_Failed|$orderId|$productId|$amount|Communication failure")
            val record = PaymentRecord(orderId, productId, amount, "FAILED", System.currentTimeMillis())
            paymentHistory(orderId) = (record, System.currentTimeMillis())
            FileStore.savePayment(record)
            replyTo ! PaymentFailed(orderId, "Communication failure with payment gateway")
            Behaviors.same
          } else {
            if (amount < requiredPrice) {
              FileStore.logEvent(s"Payment_Failed|$orderId|$productId|$amount|Insufficient funds")
              val record = PaymentRecord(orderId, productId, amount, "FAILED", System.currentTimeMillis())
              paymentHistory(orderId) = (record, System.currentTimeMillis())
              FileStore.savePayment(record)
              replyTo ! PaymentFailed(orderId, f"Insufficient funds! You need $$$requiredPrice%.2f, but only provided $$$amount%.2f")
              Behaviors.same
            } else if (amount > MAX_PAYMENT) {
              FileStore.logEvent(s"Payment_Failed|$orderId|$productId|$amount|Amount too large")
              val record = PaymentRecord(orderId, productId, amount, "FAILED", System.currentTimeMillis())
              paymentHistory(orderId) = (record, System.currentTimeMillis())
              FileStore.savePayment(record)
              replyTo ! PaymentFailed(orderId, s"Payment amount cannot exceed $MAX_PAYMENT")
              Behaviors.same
            } else {
              val paymentSucceeded = simulatePaymentGateway(amount)

              if (paymentSucceeded) {
                FileStore.logEvent(s"Payment_Success|$orderId|$productId|$amount")
                val record = PaymentRecord(orderId, productId, amount, "SUCCESS", System.currentTimeMillis())
                paymentHistory(orderId) = (record, System.currentTimeMillis())
                FileStore.savePayment(record)
                replyTo ! PaymentSuccessful(orderId, amount)
                paymentBehavior(paymentHistory)
              } else {
                FileStore.logEvent(s"Payment_Failed|$orderId|$productId|$amount|Gateway declined")
                val record = PaymentRecord(orderId, productId, amount, "FAILED", System.currentTimeMillis())
                paymentHistory(orderId) = (record, System.currentTimeMillis())
                FileStore.savePayment(record)
                replyTo ! PaymentFailed(orderId, "Payment declined by payment gateway (Simulated failure)")
                Behaviors.same
              }
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
