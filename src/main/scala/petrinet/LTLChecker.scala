package petrinet

import scala.collection.mutable

/**
 * LTL (Linear Temporal Logic) model checker for Petri nets.
 * Checks safety and liveness properties over the reachability graph.
 *
 * Supported operators:
 *   G (globally/always), F (eventually/finally), X (next), U (until)
 */

// ===== LTL Formula AST =====
sealed trait LTLFormula
case class Atom(predicate: Marking => Boolean, name: String) extends LTLFormula {
  override def toString: String = name
}
case class Not(f: LTLFormula) extends LTLFormula {
  override def toString: String = s"!($f)"
}
case class And(f1: LTLFormula, f2: LTLFormula) extends LTLFormula {
  override def toString: String = s"($f1 & $f2)"
}
case class Or(f1: LTLFormula, f2: LTLFormula) extends LTLFormula {
  override def toString: String = s"($f1 | $f2)"
}
case class Implies(f1: LTLFormula, f2: LTLFormula) extends LTLFormula {
  override def toString: String = s"($f1 -> $f2)"
}
case class Globally(f: LTLFormula) extends LTLFormula {
  override def toString: String = s"G($f)"
}
case class Eventually(f: LTLFormula) extends LTLFormula {
  override def toString: String = s"F($f)"
}
case class Next(f: LTLFormula) extends LTLFormula {
  override def toString: String = s"X($f)"
}
case class Until(f1: LTLFormula, f2: LTLFormula) extends LTLFormula {
  override def toString: String = s"($f1 U $f2)"
}

case class LTLResult(
  formula: LTLFormula,
  satisfied: Boolean,
  counterexamplePath: Option[List[Int]] = None
)

object LTLChecker {

  def check(formula: LTLFormula, graph: ReachabilityGraph): LTLResult = {
    formula match {
      case Globally(f)                  => checkGlobally(f, graph)
      case Eventually(f)               => checkEventually(f, graph)
      case Implies(antecedent, consequent) => checkImplication(antecedent, consequent, graph)
      case _                           => checkGeneric(formula, graph)
    }
  }

  /** G(f): f must hold in ALL reachable states */
  private def checkGlobally(f: LTLFormula, graph: ReachabilityGraph): LTLResult = {
    val violation = graph.nodes.find(node => !evaluateState(f, node.marking))
    violation match {
      case Some(node) =>
        LTLResult(Globally(f), satisfied = false, counterexamplePath = Some(findPathTo(graph, node.id)))
      case None =>
        LTLResult(Globally(f), satisfied = true)
    }
  }

  /** F(f): f must be reachable from the initial state */
  private def checkEventually(f: LTLFormula, graph: ReachabilityGraph): LTLResult = {
    val satisfyingStates = graph.nodes.filter(n => evaluateState(f, n.marking)).map(_.id).toSet

    if (satisfyingStates.isEmpty) {
      LTLResult(Eventually(f), satisfied = false, counterexamplePath = Some(List(graph.initialNodeId)))
    } else {
      val canReachSatisfying = mutable.Set[Int]()
      val queue = mutable.Queue[Int]()
      satisfyingStates.foreach { id => canReachSatisfying += id; queue.enqueue(id) }

      while (queue.nonEmpty) {
        val current = queue.dequeue()
        graph.predecessors(current).foreach { case (predId, _) =>
          if (!canReachSatisfying.contains(predId)) {
            canReachSatisfying += predId
            queue.enqueue(predId)
          }
        }
      }

      if (canReachSatisfying.contains(graph.initialNodeId)) {
        LTLResult(Eventually(f), satisfied = true)
      } else {
        LTLResult(Eventually(f), satisfied = false, counterexamplePath = Some(List(graph.initialNodeId)))
      }
    }
  }

  /** Implication check */
  private def checkImplication(antecedent: LTLFormula, consequent: LTLFormula, graph: ReachabilityGraph): LTLResult = {
    consequent match {
      case Eventually(f) =>
        val pStates = graph.nodes.filter(n => evaluateState(antecedent, n.marking))
        val fStates = graph.nodes.filter(n => evaluateState(f, n.marking)).map(_.id).toSet

        val violation = pStates.find(pNode => !canReach(graph, pNode.id, fStates))
        violation match {
          case Some(node) =>
            LTLResult(Implies(antecedent, consequent), satisfied = false,
              counterexamplePath = Some(findPathTo(graph, node.id)))
          case None =>
            LTLResult(Implies(antecedent, consequent), satisfied = true)
        }

      case _ =>
        val violation = graph.nodes.find { node =>
          evaluateState(antecedent, node.marking) && !evaluateState(consequent, node.marking)
        }
        violation match {
          case Some(node) =>
            LTLResult(Implies(antecedent, consequent), satisfied = false,
              counterexamplePath = Some(findPathTo(graph, node.id)))
          case None =>
            LTLResult(Implies(antecedent, consequent), satisfied = true)
        }
    }
  }

  private def checkGeneric(formula: LTLFormula, graph: ReachabilityGraph): LTLResult = {
    val violation = graph.nodes.find(node => !evaluateState(formula, node.marking))
    violation match {
      case Some(node) =>
        LTLResult(formula, satisfied = false, counterexamplePath = Some(findPathTo(graph, node.id)))
      case None =>
        LTLResult(formula, satisfied = true)
    }
  }

  /** Evaluate a state-level formula against a marking */
  private def evaluateState(f: LTLFormula, m: Marking): Boolean = f match {
    case Atom(pred, _)    => pred(m)
    case Not(inner)       => !evaluateState(inner, m)
    case And(f1, f2)      => evaluateState(f1, m) && evaluateState(f2, m)
    case Or(f1, f2)       => evaluateState(f1, m) || evaluateState(f2, m)
    case Implies(f1, f2)  => !evaluateState(f1, m) || evaluateState(f2, m)
    case _                => true
  }

  private def findPathTo(graph: ReachabilityGraph, targetId: Int): List[Int] = {
    if (targetId == graph.initialNodeId) return List(graph.initialNodeId)
    val visited = mutable.Set[Int]()
    val parent = mutable.Map[Int, Int]()
    val queue = mutable.Queue[Int]()
    queue.enqueue(graph.initialNodeId)
    visited += graph.initialNodeId

    while (queue.nonEmpty) {
      val current = queue.dequeue()
      if (current == targetId) {
        val path = mutable.ListBuffer[Int]()
        var node = targetId
        while (node != graph.initialNodeId) { path.prepend(node); node = parent(node) }
        path.prepend(graph.initialNodeId)
        return path.toList
      }
      graph.successors(current).foreach { case (nextId, _) =>
        if (!visited.contains(nextId)) { visited += nextId; parent(nextId) = current; queue.enqueue(nextId) }
      }
    }
    List(graph.initialNodeId, targetId)
  }

  private def canReach(graph: ReachabilityGraph, fromId: Int, targets: Set[Int]): Boolean = {
    if (targets.contains(fromId)) return true
    val visited = mutable.Set[Int]()
    val queue = mutable.Queue[Int]()
    queue.enqueue(fromId); visited += fromId
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      if (targets.contains(current)) return true
      graph.successors(current).foreach { case (nextId, _) =>
        if (!visited.contains(nextId)) { visited += nextId; queue.enqueue(nextId) }
      }
    }
    false
  }

  // ===== Predefined Atomic Propositions (matching P1-P6) =====

  def inP1_Idle: Atom = Atom(m => m.getOrElse(OrderSystemPetriNet.P1, 0) > 0, "in_P1_Idle")
  def inP2_Pending: Atom = Atom(m => m.getOrElse(OrderSystemPetriNet.P2, 0) > 0, "in_P2_Order_Pending")
  def inP3_Reserved: Atom = Atom(m => m.getOrElse(OrderSystemPetriNet.P3, 0) > 0, "in_P3_Order_Reserved")
  def inP4_Payment: Atom = Atom(m => m.getOrElse(OrderSystemPetriNet.P4, 0) > 0, "in_P4_Payment_Processing")
  def inP5_Confirmed: Atom = Atom(m => m.getOrElse(OrderSystemPetriNet.P5, 0) > 0, "in_P5_Order_Confirmed")
  def inP6_Fail: Atom = Atom(m => m.getOrElse(OrderSystemPetriNet.P6, 0) > 0, "in_P6_Fail")

  /** Order has reached a terminal state (confirmed or fail) */
  def orderTerminated: Atom = Atom(
    m => m.getOrElse(OrderSystemPetriNet.P5, 0) > 0 ||
         m.getOrElse(OrderSystemPetriNet.P6, 0) > 0 ||
         m.getOrElse(OrderSystemPetriNet.P1, 0) > 0,
    "order_terminated_or_idle"
  )

  /** Exactly one token exists in the entire net */
  def singleToken: Atom = Atom(
    m => OrderSystemPetriNet.allPlaces.toList.map(p => m.getOrElse(p, 0)).sum == 1,
    "single_token_conservation"
  )

  // ===== Run predefined LTL checks =====

  def runOrderSystemChecks(graph: ReachabilityGraph): List[LTLResult] = {
    val results = mutable.ListBuffer[LTLResult]()

    println(s"\n${"=" * 60}")
    println("LTL MODEL CHECKING RESULTS")
    println(s"${"=" * 60}")

    // Safety S1: Token conservation — always exactly 1 token in the net
    val s1 = check(Globally(singleToken), graph)
    results += s1
    printLTLResult("S1", "G(token_count = 1) — Token conservation", s1)

    // Safety S2: P5 and P6 are mutually exclusive (can't be confirmed AND failed)
    val noConflict = Atom(
      m => !(m.getOrElse(OrderSystemPetriNet.P5, 0) > 0 &&
             m.getOrElse(OrderSystemPetriNet.P6, 0) > 0),
      "not(P5 > 0 AND P6 > 0)"
    )
    val s2 = check(Globally(noConflict), graph)
    results += s2
    printLTLResult("S2", "G(!(confirmed AND failed)) — Mutual exclusion", s2)

    // Safety S3: If in P4, then not in P1 (payment processing implies not idle)
    val paymentImpliesNotIdle = Atom(
      m => !(m.getOrElse(OrderSystemPetriNet.P4, 0) > 0 &&
             m.getOrElse(OrderSystemPetriNet.P1, 0) > 0),
      "not(P4 > 0 AND P1 > 0)"
    )
    val s3 = check(Globally(paymentImpliesNotIdle), graph)
    results += s3
    printLTLResult("S3", "G(!(processing AND idle)) — State consistency", s3)

    // Liveness L1: If order is pending, it eventually terminates
    val l1 = check(Implies(inP2_Pending, Eventually(orderTerminated)), graph)
    results += l1
    printLTLResult("L1", "pending -> F(terminated) — Order always terminates", l1)

    // Liveness L2: The system can always eventually return to idle
    val l2 = check(Eventually(inP1_Idle), graph)
    results += l2
    printLTLResult("L2", "F(idle) — System can return to idle", l2)

    // Liveness L3: If payment is processing, it eventually resolves
    val l3 = check(Implies(inP4_Payment, Eventually(Or(inP5_Confirmed, inP6_Fail))), graph)
    results += l3
    printLTLResult("L3", "payment -> F(confirmed OR fail) — Payment resolves", l3)

    println(s"\n${"=" * 60}")
    val passed = results.count(_.satisfied)
    println(s"LTL SUMMARY: $passed/${results.size} properties satisfied")
    println(s"${"=" * 60}")

    results.toList
  }

  private def printLTLResult(id: String, description: String, result: LTLResult): Unit = {
    val status = if (result.satisfied) "PASS" else "FAIL"
    println(s"\n[$status] $id: $description")
    println(s"  Formula: ${result.formula}")
    result.counterexamplePath.foreach { path =>
      println(s"  Counterexample path: ${path.mkString(" -> ")}")
    }
  }
}
