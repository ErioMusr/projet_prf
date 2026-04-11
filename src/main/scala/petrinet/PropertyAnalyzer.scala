package petrinet

/**
 * Structural and behavioral property analyzer for Petri nets.
 * Checks deadlock absence, boundedness, liveness, invariants, and business constraints.
 */
object PropertyAnalyzer {

  case class AnalysisResult(
    property: String,
    satisfied: Boolean,
    details: String
  )

  // ===== Structural Properties =====

  /** Check deadlock absence: no reachable marking with zero enabled transitions */
  def checkDeadlockFreedom(net: PetriNet, graph: ReachabilityGraph): AnalysisResult = {
    val deadlocks = graph.deadlockStates
    if (deadlocks.isEmpty) {
      AnalysisResult("Deadlock Freedom", satisfied = true,
        s"No deadlocks found among ${graph.nodeCount} reachable states.")
    } else {
      val details = deadlocks.map { node =>
        s"  State #${node.id}: ${formatMarking(node.marking)}"
      }.mkString("\n")
      AnalysisResult("Deadlock Freedom", satisfied = false,
        s"${deadlocks.size} deadlock state(s) found:\n$details")
    }
  }

  /** Check boundedness: every place has a finite max token count */
  def checkBoundedness(net: PetriNet, graph: ReachabilityGraph): AnalysisResult = {
    val maxTokens = net.places.map { place =>
      val maxInPlace = graph.nodes.map(_.marking.getOrElse(place, 0)).max
      place -> maxInPlace
    }.toMap

    val maxOverall = maxTokens.values.max
    val details = maxTokens.toList.sortBy(_._1.id).map { case (place, max) =>
      s"  ${place.id}: max = $max"
    }.mkString("\n")

    AnalysisResult("Boundedness", satisfied = true,
      s"Net is ${maxOverall}-bounded.\n$details")
  }

  /** Check transition validity: every transition can fire from at least one reachable state */
  def checkTransitionValidity(net: PetriNet, graph: ReachabilityGraph): AnalysisResult = {
    val firedTransitions = graph.edges.map(_.transition).toSet
    val deadTransitions = net.transitions -- firedTransitions

    if (deadTransitions.isEmpty) {
      AnalysisResult("Transition Validity", satisfied = true,
        s"All ${net.transitions.size} transitions are valid (can fire).")
    } else {
      val details = deadTransitions.map(t => s"  ${t.id}").mkString("\n")
      AnalysisResult("Transition Validity", satisfied = false,
        s"${deadTransitions.size} dead transition(s) found (never fire):\n$details")
    }
  }

  /** Check liveness (L1): every transition can eventually fire from some reachable state */
  def checkLiveness(net: PetriNet, graph: ReachabilityGraph): AnalysisResult = {
    val firedTransitions = graph.edges.map(_.transition).toSet
    val neverFired = net.transitions -- firedTransitions

    if (neverFired.isEmpty) {
      AnalysisResult("Liveness (L1)", satisfied = true,
        s"All transitions are L1-live (each can fire at least once).")
    } else {
      val details = neverFired.map(t => s"  ${t.id}: never fires").mkString("\n")
      AnalysisResult("Liveness (L1)", satisfied = false,
        s"${neverFired.size} transition(s) are not L1-live:\n$details")
    }
  }

  // ===== Business Invariants =====

  /** Check: inventory tokens are never negative in any reachable state */
  def checkInventoryNonNegativity(graph: ReachabilityGraph, inventoryPlace: Place): AnalysisResult = {
    val violations = graph.nodes.filter { node =>
      node.marking.getOrElse(inventoryPlace, 0) < 0
    }

    if (violations.isEmpty) {
      AnalysisResult("Inventory Non-Negativity", satisfied = true,
        s"Inventory is >= 0 in all ${graph.nodeCount} reachable states.")
    } else {
      val details = violations.map { node =>
        s"  State #${node.id}: inventory = ${node.marking.getOrElse(inventoryPlace, 0)}"
      }.mkString("\n")
      AnalysisResult("Inventory Non-Negativity", satisfied = false,
        s"${violations.size} state(s) violate inventory non-negativity:\n$details")
    }
  }

  /** Check: an order is in at most one state at any time (mutual exclusion of order states) */
  def checkOrderStateMutualExclusion(graph: ReachabilityGraph, orderStatePlaces: Set[Place]): AnalysisResult = {
    val violations = graph.nodes.filter { node =>
      val activeStates = orderStatePlaces.count(p => node.marking.getOrElse(p, 0) > 0)
      activeStates > 1
    }

    if (violations.isEmpty) {
      AnalysisResult("Order State Mutual Exclusion", satisfied = true,
        s"Order is in at most one state in all ${graph.nodeCount} reachable states.")
    } else {
      val details = violations.take(5).map { node =>
        val activeStates = orderStatePlaces.filter(p => node.marking.getOrElse(p, 0) > 0)
        s"  State #${node.id}: active in ${activeStates.map(_.id).mkString(", ")}"
      }.mkString("\n")
      AnalysisResult("Order State Mutual Exclusion", satisfied = false,
        s"${violations.size} state(s) violate mutual exclusion:\n$details")
    }
  }

  /** Check token conservation across a set of places */
  def checkTokenConservation(graph: ReachabilityGraph, conservedPlaces: Set[Place], expectedTotal: Int): AnalysisResult = {
    val violations = graph.nodes.filter { node =>
      val total = conservedPlaces.toList.map(p => node.marking.getOrElse(p, 0)).sum
      total != expectedTotal
    }

    if (violations.isEmpty) {
      AnalysisResult("Token Conservation", satisfied = true,
        s"Total tokens across ${conservedPlaces.map(_.id).mkString(", ")} = $expectedTotal in all states.")
    } else {
      val details = violations.take(5).map { node =>
        val total = conservedPlaces.toList.map(p => node.marking.getOrElse(p, 0)).sum
        s"  State #${node.id}: total = $total (expected $expectedTotal)"
      }.mkString("\n")
      AnalysisResult("Token Conservation", satisfied = false,
        s"${violations.size} state(s) violate conservation:\n$details")
    }
  }

  // ===== Incidence Matrix and Invariants =====

  /** Compute S-invariants (place invariants): vectors y such that y^T * C = 0 */
  def computeSInvariants(net: PetriNet): List[Map[Place, Int]] = {
    val matrix = net.incidenceMatrix
    val placeList = net.places.toList.sortBy(_.id)
    val transList = net.transitions.toList.sortBy(_.id)

    // Simple brute-force search for small nets: try unit vectors and simple combinations
    val invariants = scala.collection.mutable.ListBuffer[Map[Place, Int]]()

    // Check individual places (trivially conserved if all transitions have 0 net effect)
    for (p <- placeList) {
      val allZero = transList.forall(t => matrix(p)(t) == 0)
      if (allZero) {
        invariants += Map(p -> 1)
      }
    }

    // Check pairs of places
    for (i <- placeList.indices; j <- (i + 1) until placeList.size) {
      val p1 = placeList(i)
      val p2 = placeList(j)
      // Check if p1 + p2 is an S-invariant
      val isInvariant = transList.forall { t =>
        matrix(p1)(t) + matrix(p2)(t) == 0
      }
      if (isInvariant) {
        invariants += Map(p1 -> 1, p2 -> 1)
      }
      // Check if p1 - p2 is an S-invariant (with coefficient -1)
      val isInvariantNeg = transList.forall { t =>
        matrix(p1)(t) - matrix(p2)(t) == 0
      }
      if (isInvariantNeg) {
        invariants += Map(p1 -> 1, p2 -> -1)
      }
    }

    invariants.toList
  }

  /** Compute T-invariants (transition invariants): vectors x such that C * x = 0 */
  def computeTInvariants(net: PetriNet): List[Map[Transition, Int]] = {
    val matrix = net.incidenceMatrix
    val placeList = net.places.toList.sortBy(_.id)
    val transList = net.transitions.toList.sortBy(_.id)

    val invariants = scala.collection.mutable.ListBuffer[Map[Transition, Int]]()

    // Check individual transitions (self-loops)
    for (t <- transList) {
      val allZero = placeList.forall(p => matrix(p)(t) == 0)
      if (allZero) {
        invariants += Map(t -> 1)
      }
    }

    // Check pairs
    for (i <- transList.indices; j <- (i + 1) until transList.size) {
      val t1 = transList(i)
      val t2 = transList(j)
      val isInvariant = placeList.forall { p =>
        matrix(p)(t1) + matrix(p)(t2) == 0
      }
      if (isInvariant) {
        invariants += Map(t1 -> 1, t2 -> 1)
      }
    }

    invariants.toList
  }

  // ===== Report Generation =====

  /** Run all analyses and print a full report */
  def runFullAnalysis(net: PetriNet, graph: ReachabilityGraph): List[AnalysisResult] = {
    val results = scala.collection.mutable.ListBuffer[AnalysisResult]()

    println(s"\n${"=" * 60}")
    println("PETRI NET PROPERTY ANALYSIS REPORT")
    println(s"${"=" * 60}")

    // Structural properties
    val deadlockResult = checkDeadlockFreedom(net, graph)
    results += deadlockResult
    printResult(deadlockResult)

    val boundednessResult = checkBoundedness(net, graph)
    results += boundednessResult
    printResult(boundednessResult)

    val transitionResult = checkTransitionValidity(net, graph)
    results += transitionResult
    printResult(transitionResult)

    val livenessResult = checkLiveness(net, graph)
    results += livenessResult
    printResult(livenessResult)

    // Business invariants
    val invResult = checkInventoryNonNegativity(graph, OrderSystemPetriNet.P_inventory)
    results += invResult
    printResult(invResult)

    val orderStatePlaces = Set(
      OrderSystemPetriNet.P_pending_inventory,
      OrderSystemPetriNet.P_stock_reserved,
      OrderSystemPetriNet.P_payment_processing,
      OrderSystemPetriNet.P_payment_success,
      OrderSystemPetriNet.P_payment_failed,
      OrderSystemPetriNet.P_inv_timeout,
      OrderSystemPetriNet.P_pay_timeout
    )
    val mutexResult = checkOrderStateMutualExclusion(graph, orderStatePlaces)
    results += mutexResult
    printResult(mutexResult)

    // S-invariants and T-invariants
    println(s"\n--- S-Invariants (Place Invariants) ---")
    val sInvariants = computeSInvariants(net)
    if (sInvariants.isEmpty) {
      println("  No S-invariants found via simple search.")
    } else {
      sInvariants.foreach { inv =>
        val terms = inv.toList.sortBy(_._1.id).map { case (p, coeff) =>
          if (coeff == 1) p.id else s"$coeff*${p.id}"
        }.mkString(" + ")
        println(s"  $terms = const")
      }
    }

    println(s"\n--- T-Invariants (Transition Invariants) ---")
    val tInvariants = computeTInvariants(net)
    if (tInvariants.isEmpty) {
      println("  No T-invariants found via simple search.")
    } else {
      tInvariants.foreach { inv =>
        val terms = inv.toList.sortBy(_._1.id).map { case (t, coeff) =>
          if (coeff == 1) t.id else s"$coeff*${t.id}"
        }.mkString(" + ")
        println(s"  $terms = 0")
      }
    }

    println(s"\n${"=" * 60}")
    val passed = results.count(_.satisfied)
    val total = results.size
    println(s"SUMMARY: $passed/$total properties satisfied")
    println(s"${"=" * 60}")

    results.toList
  }

  private def printResult(result: AnalysisResult): Unit = {
    val status = if (result.satisfied) "PASS" else "FAIL"
    println(s"\n[$status] ${result.property}")
    println(s"  ${result.details}")
  }

  private def formatMarking(m: Marking): String =
    m.filter(_._2 > 0).toList.sortBy(_._1.id).map { case (p, n) => s"${p.id}=$n" }.mkString(", ")
}
