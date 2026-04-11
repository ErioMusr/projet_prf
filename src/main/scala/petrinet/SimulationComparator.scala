package petrinet

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Compares Akka execution traces (from log.txt) with Petri net model behavior.
 * Validates that observed Akka behaviors correspond to valid Petri net executions.
 */
object SimulationComparator {

  case class AkkaEvent(timestamp: Long, eventType: String, details: List[String])

  case class ComparisonResult(
    totalAkkaEvents: Int,
    mappedEvents: Int,
    unmappedEvents: Int,
    validTransitions: Int,
    invalidTransitions: Int,
    discrepancies: List[String]
  )

  /** Map Akka log event types to Petri net transition IDs */
  private val eventToTransitionMap: Map[String, String] = Map(
    "PlaceOrder"                  -> "T_place_order",
    "Check_Inv_Start"             -> "T_check_inventory",
    "Inv_Enough_Reduce_Success"   -> "T_inventory_ok",
    "Inv_Insufficient"            -> "T_inventory_fail",
    "Inv_NotFound"                -> "T_item_not_found",
    "Submit_Payment"              -> "T_process_payment",
    "Payment_Success"             -> "T_payment_ok",
    "Payment_Failed"              -> "T_payment_fail",
    "Check_Inv_Timeout"           -> "T_inv_timeout",
    "Payment_Timeout"             -> "T_pay_timeout"
  )

  /** Parse the Akka log file (log.txt) into structured events */
  def parseAkkaLog(logPath: String): List[AkkaEvent] = {
    val path = Paths.get(logPath)
    if (!Files.exists(path)) {
      println(s"[WARNING] Log file not found: $logPath")
      return List()
    }

    Files.readAllLines(path).asScala.flatMap { line =>
      val parts = line.split("\\|").toList
      if (parts.size >= 2) {
        val timestamp = try { parts.head.toLong } catch { case _: Exception => 0L }
        val eventType = parts(1)
        val details = parts.drop(2)
        Some(AkkaEvent(timestamp, eventType, details))
      } else None
    }.toList
  }

  /** Map Akka events to Petri net transition sequence */
  def mapToTransitions(events: List[AkkaEvent]): (List[(AkkaEvent, String)], List[AkkaEvent]) = {
    val mapped = mutable.ListBuffer[(AkkaEvent, String)]()
    val unmapped = mutable.ListBuffer[AkkaEvent]()

    events.foreach { event =>
      eventToTransitionMap.get(event.eventType) match {
        case Some(transitionId) => mapped += ((event, transitionId))
        case None => unmapped += event
      }
    }

    (mapped.toList, unmapped.toList)
  }

  /**
   * Validate that the Akka execution trace is consistent with the Petri net model.
   * Replays the mapped transitions on the Petri net and checks for invalid firings.
   */
  def validateTrace(net: PetriNet, initialMarking: Marking, events: List[AkkaEvent]): ComparisonResult = {
    val (mapped, unmapped) = mapToTransitions(events)
    val discrepancies = mutable.ListBuffer[String]()
    var currentMarking = initialMarking
    var validCount = 0
    var invalidCount = 0

    // Group events by order (using details to extract orderId)
    // For simplicity, validate the global sequence
    mapped.foreach { case (event, transitionId) =>
      net.transitions.find(_.id == transitionId) match {
        case Some(transition) =>
          if (net.isEnabled(transition, currentMarking)) {
            currentMarking = net.fire(transition, currentMarking).get
            validCount += 1
          } else {
            invalidCount += 1
            discrepancies += s"Transition $transitionId not enabled at timestamp ${event.timestamp} " +
              s"(event: ${event.eventType}, details: ${event.details.mkString(",")})"
          }
        case None =>
          invalidCount += 1
          discrepancies += s"Unknown transition $transitionId for event ${event.eventType}"
      }
    }

    ComparisonResult(
      totalAkkaEvents = events.size,
      mappedEvents = mapped.size,
      unmappedEvents = unmapped.size,
      validTransitions = validCount,
      invalidTransitions = invalidCount,
      discrepancies = discrepancies.toList
    )
  }

  /** Run the full comparative analysis */
  def runComparison(net: PetriNet, initialMarking: Marking, logPath: String = "log.txt"): ComparisonResult = {
    println(s"\n${"=" * 60}")
    println("COMPARATIVE SIMULATION: Akka vs Petri Net")
    println(s"${"=" * 60}")

    val events = parseAkkaLog(logPath)
    println(s"Parsed ${events.size} events from Akka log")

    if (events.isEmpty) {
      println("[INFO] No events to compare. Run the Akka system first to generate log.txt")
      return ComparisonResult(0, 0, 0, 0, 0, List())
    }

    // Show event distribution
    println("\n--- Event Distribution ---")
    val eventCounts = events.groupBy(_.eventType).map { case (t, evts) => (t, evts.size) }
    eventCounts.toList.sortBy(-_._2).foreach { case (eventType, count) =>
      val mappedTo = eventToTransitionMap.getOrElse(eventType, "(unmapped)")
      println(f"  $eventType%-35s -> $mappedTo%-25s (count: $count)")
    }

    // Validate trace
    val result = validateTrace(net, initialMarking, events)

    println(s"\n--- Validation Results ---")
    println(s"  Total Akka events:      ${result.totalAkkaEvents}")
    println(s"  Mapped to transitions:  ${result.mappedEvents}")
    println(s"  Unmapped events:        ${result.unmappedEvents}")
    println(s"  Valid firings:          ${result.validTransitions}")
    println(s"  Invalid firings:        ${result.invalidTransitions}")

    if (result.discrepancies.nonEmpty) {
      println(s"\n--- Discrepancies (${result.discrepancies.size}) ---")
      result.discrepancies.take(10).foreach(d => println(s"  - $d"))
      if (result.discrepancies.size > 10) {
        println(s"  ... and ${result.discrepancies.size - 10} more")
      }
    } else {
      println("\n  [OK] All mapped events correspond to valid Petri net firings!")
    }

    val consistency = if (result.invalidTransitions == 0) "CONSISTENT" else "INCONSISTENT"
    println(s"\n--- Conclusion ---")
    println(s"  Akka behavior is $consistency with the Petri net model.")
    if (result.invalidTransitions > 0) {
      println(s"  The Petri net model may be too restrictive or the Akka system")
      println(s"  allows behaviors not captured in the model (e.g., concurrent orders).")
    }

    println(s"${"=" * 60}")
    result
  }
}
