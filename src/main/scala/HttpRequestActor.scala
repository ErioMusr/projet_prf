import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.mutable

object HttpRequestActor {
  private val REQUEST_TIMEOUT_MS = 5000
  private val MAX_RETRIES = 3

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("HttpRequestActor initialized")
    httpBehavior(mutable.Map[String, HttpRequest]())
  }

  private def httpBehavior(activeRequests: mutable.Map[String, HttpRequest]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SendHttpRequest(orderId, endpoint, data) =>
          context.log.info(s"Sending HTTP request for order: $orderId to endpoint: $endpoint")

          // Validate inputs
          if (endpoint.isEmpty || data.isEmpty) {
            context.log.warn(s"Invalid HTTP request: endpoint=$endpoint, data=$data")
            Behaviors.same
          } else {
            val httpRequest = HttpRequest(orderId, endpoint, data, "PENDING", System.currentTimeMillis())
            activeRequests(orderId) = httpRequest

            val requestSucceeded = simulateHttpRequest()

            if (requestSucceeded) {
              context.log.info(s"HTTP request successful for order: $orderId")
              activeRequests(orderId) = httpRequest.copy(status = "SUCCESS")
              // replyTo ! HttpSuccess(orderId, "Response data")
            } else {
              context.log.warn(s"HTTP request failed for order: $orderId")
              activeRequests(orderId) = httpRequest.copy(status = "FAILED")
              // replyTo ! HttpError(orderId, "Connection timeout")
            }

            Behaviors.same
          }

        case _ =>
          context.log.warn("Unhandled message in HttpRequestActor")
          Behaviors.unhandled
      }
    }

  private def simulateHttpRequest(): Boolean = {
    scala.util.Random.nextDouble() < 0.9
  }
}

case class HttpRequest(
  orderId: String,
  endpoint: String,
  data: String,
  status: String,           // PENDING, SUCCESS, FAILED
  timestamp: Long
)

