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
    discrepancies: List[String],
    skippedMappedEvents: Int = 0
  )

  case class OrderTrace(orderId: String, events: List[AkkaEvent])

  case class OrderValidationResult(orderId: String, result: ComparisonResult)

  case class MultiOrderComparisonResult(
    summary: ComparisonResult,
    orderResults: List[OrderValidationResult],
    groupedOrders: Int,
    groupedEvents: Int
  )

  /** Map Akka log event types to Petri net transition IDs (matching diagram) */
  private val eventToTransitionMap: Map[String, String] = Map(
    "PlaceOrder"                  -> "T1_PlaceOrder",
    "Inv_NotFound"                -> "T2_Inv_NotFound",
    "Check_Inv_Timeout"           -> "T3_Check_Timeout",
    "Inv_Insufficient"            -> "T4_Inv_Insufficient",
    "Inv_Enough_Reduce_Success"   -> "T5_Inv_Success",
    "Payment_Timeout"             -> "T6_Pay_Timeout",
    "Submit_Payment"              -> "T7_Submit_Pay",
    "Payment_Failed"              -> "T8_Pay_Failed",
    "Payment_Success"             -> "T9_Pay_Success"
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

  private def extractOrderId(event: AkkaEvent): Option[String] = {
    event.details.find(_.startsWith("order-"))
  }

  private def placeOrderSignature(event: AkkaEvent): Option[(String, String)] = {
    if (event.eventType == "PlaceOrder") {
      for {
        productId <- event.details.headOption
        quantity <- event.details.lift(1)
      } yield (productId, quantity)
    } else None
  }

  private def eventSignatureForMatching(event: AkkaEvent): Option[(String, String)] = {
    for {
      productId <- event.details.lift(1)
      quantity <- event.details.lift(2)
    } yield (productId, quantity)
  }

  private def selectMatchingPlaceOrder(
    pendingPlaceOrders: mutable.ListBuffer[AkkaEvent],
    event: AkkaEvent
  ): Option[AkkaEvent] = {
    if (pendingPlaceOrders.isEmpty) return None

    val signature = eventSignatureForMatching(event)
    val exactIndex = signature.flatMap { sig =>
      val idx = pendingPlaceOrders.lastIndexWhere { po =>
        po.timestamp <= event.timestamp && placeOrderSignature(po).contains(sig)
      }
      if (idx >= 0) Some(idx) else None
    }

    val fallbackIndex = {
      val idx = pendingPlaceOrders.lastIndexWhere(_.timestamp <= event.timestamp)
      if (idx >= 0) Some(idx) else None
    }

    (exactIndex orElse fallbackIndex).map(pendingPlaceOrders.remove)
  }

  /**
   * Group events by order ID so each order can be replayed on its own initial marking.
   * PlaceOrder events do not carry orderId; they are attached to the first matching order event.
   */
  def groupByOrder(events: List[AkkaEvent]): Map[String, List[AkkaEvent]] = {
    val ordered = events.sortBy(_.timestamp)
    val pendingPlaceOrders = mutable.ListBuffer[AkkaEvent]()
    val grouped = mutable.LinkedHashMap[String, mutable.ListBuffer[AkkaEvent]]()

    ordered.foreach { event =>
      extractOrderId(event) match {
        case Some(orderId) =>
          val trace = grouped.getOrElseUpdate(orderId, mutable.ListBuffer[AkkaEvent]())
          if (trace.isEmpty) {
            selectMatchingPlaceOrder(pendingPlaceOrders, event).foreach(trace += _)
          }
          trace += event

        case None if event.eventType == "PlaceOrder" =>
          pendingPlaceOrders += event

        case None =>
          ()
      }
    }

    grouped.view.mapValues(_.toList).toMap
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

  def validateTraceByOrder(net: PetriNet, initialMarking: Marking, events: List[AkkaEvent]): MultiOrderComparisonResult = {
    val grouped = groupByOrder(events)
    val orderResults = grouped.toList.sortBy(_._1).map { case (orderId, traceEvents) =>
      val result = validateTrace(net, initialMarking, traceEvents)
      OrderValidationResult(orderId, result)
    }

    val (mappedAll, unmappedAll) = mapToTransitions(events)
    val totalValid = orderResults.map(_.result.validTransitions).sum
    val totalInvalid = orderResults.map(_.result.invalidTransitions).sum
    val discrepancies = orderResults.flatMap { order =>
      order.result.discrepancies.map(d => s"[${order.orderId}] $d")
    }
    val groupedMapped = orderResults.map(_.result.mappedEvents).sum
    val groupedEventCount = grouped.values.map(_.size).sum
    val skippedMapped = (mappedAll.size - groupedMapped).max(0)

    val summary = ComparisonResult(
      totalAkkaEvents = events.size,
      mappedEvents = mappedAll.size,
      unmappedEvents = unmappedAll.size,
      validTransitions = totalValid,
      invalidTransitions = totalInvalid,
      discrepancies = discrepancies,
      skippedMappedEvents = skippedMapped
    )

    MultiOrderComparisonResult(summary, orderResults, grouped.size, groupedEventCount)
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

    val multiOrderResult = validateTraceByOrder(net, initialMarking, events)
    val result = multiOrderResult.summary

    println(s"\n--- Validation Results ---")
    println(s"  Total Akka events:      ${result.totalAkkaEvents}")
    println(s"  Grouped orders:         ${multiOrderResult.groupedOrders}")
    println(s"  Events in order traces: ${multiOrderResult.groupedEvents}")
    println(s"  Mapped to transitions:  ${result.mappedEvents}")
    println(s"  Unmapped events:        ${result.unmappedEvents}")
    println(s"  Skipped mapped events:  ${result.skippedMappedEvents}")
    println(s"  Valid firings:          ${result.validTransitions}")
    println(s"  Invalid firings:        ${result.invalidTransitions}")

    if (multiOrderResult.orderResults.nonEmpty) {
      println("\n--- Per-Order Summary ---")
      multiOrderResult.orderResults.foreach { order =>
        println(
          f"  ${order.orderId}%-24s -> valid=${order.result.validTransitions}%2d, " +
            f"invalid=${order.result.invalidTransitions}%2d, mapped=${order.result.mappedEvents}%2d"
        )
      }
    }

    if (result.discrepancies.nonEmpty) {
      println(s"\n--- Discrepancies (${result.discrepancies.size}) ---")
      result.discrepancies.take(10).foreach(d => println(s"  - $d"))
      if (result.discrepancies.size > 10)
        println(s"  ... and ${result.discrepancies.size - 10} more")
      println("\n  [NOTE] Remaining discrepancies are now localized to specific order traces.")
    } else {
      println("\n  [OK] All grouped mapped events correspond to valid Petri net firings!")
    }

    val consistency = if (result.invalidTransitions == 0) "CONSISTENT" else "PARTIALLY CONSISTENT"
    println(s"\n  Conclusion: Akka behavior is $consistency with the Petri net model.")
    println(s"${"=" * 60}")
    result
  }
}
