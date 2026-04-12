package petrinet

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Compares Akka execution traces (from log.txt) with Petri net model behavior.
 * Maps Akka events to P1-P6 transitions and validates consistency.
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

  /** Map Akka log event types to Petri net transition IDs (matching diagram) */
  private val eventToTransitionMap: Map[String, String] = Map(
    "PlaceOrder"                  -> "T1_PlaceOrder",
    "Inv_Enough_Reduce_Success"   -> "T2_Inv_Enough_Reduce_Success",
    "Inv_NotFound"                -> "T3_Inv_NotFound",
    "Inv_Insufficient"            -> "T4_Inv_Insufficient",
    "Check_Inv_Timeout"           -> "T5_Check_Inv_Timeout",
    "Submit_Payment"              -> "T6_Submit_Payment",
    "Payment_Success"             -> "T7_Payment_Success",
    "Payment_Failed"              -> "T8_Payment_Failed",
    "Payment_Timeout"             -> "T9_Payment_Timeout"
  )

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
        Some(AkkaEvent(timestamp, parts(1), parts.drop(2)))
      } else None
    }.toList
  }

  def mapToTransitions(events: List[AkkaEvent]): (List[(AkkaEvent, String)], List[AkkaEvent]) = {
    val mapped = mutable.ListBuffer[(AkkaEvent, String)]()
    val unmapped = mutable.ListBuffer[AkkaEvent]()
    events.foreach { event =>
      eventToTransitionMap.get(event.eventType) match {
        case Some(tId) => mapped += ((event, tId))
        case None      => unmapped += event
      }
    }
    (mapped.toList, unmapped.toList)
  }

  def validateTrace(net: PetriNet, initialMarking: Marking, events: List[AkkaEvent]): ComparisonResult = {
    val (mapped, unmapped) = mapToTransitions(events)
    val discrepancies = mutable.ListBuffer[String]()
    var currentMarking = initialMarking
    var validCount = 0
    var invalidCount = 0

    mapped.foreach { case (event, transitionId) =>
      net.transitions.find(_.id == transitionId) match {
        case Some(transition) =>
          if (net.isEnabled(transition, currentMarking)) {
            currentMarking = net.fire(transition, currentMarking).get
            validCount += 1
          } else {
            invalidCount += 1
            discrepancies += s"$transitionId not enabled at ${event.timestamp} " +
              s"| marking: ${OrderSystemPetriNet.markingVector(currentMarking)} " +
              s"| event: ${event.eventType}"
          }
        case None =>
          invalidCount += 1
          discrepancies += s"Unknown transition $transitionId"
      }
    }

    ComparisonResult(events.size, mapped.size, unmapped.size, validCount, invalidCount, discrepancies.toList)
  }

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

    println("\n--- Event Distribution ---")
    val eventCounts = events.groupBy(_.eventType).map { case (t, evts) => (t, evts.size) }
    eventCounts.toList.sortBy(-_._2).foreach { case (eventType, count) =>
      val mappedTo = eventToTransitionMap.getOrElse(eventType, "(unmapped)")
      println(f"  $eventType%-35s -> $mappedTo%-35s ($count)")
    }

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
      if (result.discrepancies.size > 10)
        println(s"  ... and ${result.discrepancies.size - 10} more")
      println("\n  [NOTE] Discrepancies are expected when log.txt contains multiple")
      println("  concurrent orders, since the Petri net models a single order flow.")
    } else {
      println("\n  [OK] All mapped events correspond to valid Petri net firings!")
    }

    val consistency = if (result.invalidTransitions == 0) "CONSISTENT" else "PARTIALLY CONSISTENT"
    println(s"\n  Conclusion: Akka behavior is $consistency with the Petri net model.")
    println(s"${"=" * 60}")
    result
  }
}
