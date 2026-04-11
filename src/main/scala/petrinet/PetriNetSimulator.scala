package petrinet

import scala.collection.mutable
import scala.util.Random

/**
 * Step-by-step Petri net simulator.
 * Executes the net by choosing enabled transitions and records execution traces.
 */

case class SimulationStep(
  stepNumber: Int,
  transition: Transition,
  markingBefore: Marking,
  markingAfter: Marking
)

case class SimulationTrace(
  steps: List[SimulationStep],
  initialMarking: Marking,
  finalMarking: Marking
)

object PetriNetSimulator {

  /**
   * Run simulation for a fixed number of steps, choosing transitions randomly.
   */
  def simulate(net: PetriNet, initialMarking: Marking, maxSteps: Int = 50, seed: Long = System.currentTimeMillis()): SimulationTrace = {
    val rng = new Random(seed)
    val steps = mutable.ListBuffer[SimulationStep]()
    var currentMarking = initialMarking

    for (step <- 1 to maxSteps) {
      val enabled = net.enabledTransitions(currentMarking).toList
      if (enabled.isEmpty) {
        println(s"  [Step $step] DEADLOCK - no transitions enabled")
        return SimulationTrace(steps.toList, initialMarking, currentMarking)
      }

      val chosen = enabled(rng.nextInt(enabled.size))
      val newMarking = net.fire(chosen, currentMarking).get

      steps += SimulationStep(step, chosen, currentMarking, newMarking)
      currentMarking = newMarking
    }

    SimulationTrace(steps.toList, initialMarking, currentMarking)
  }

  /**
   * Run simulation following a specific sequence of transitions.
   */
  def simulateSequence(net: PetriNet, initialMarking: Marking, transitionIds: List[String]): SimulationTrace = {
    val steps = mutable.ListBuffer[SimulationStep]()
    var currentMarking = initialMarking

    for ((tId, idx) <- transitionIds.zipWithIndex) {
      val transition = net.transitions.find(_.id == tId) match {
        case Some(t) => t
        case None =>
          println(s"  [Step ${idx + 1}] ERROR: Transition '$tId' not found in net")
          return SimulationTrace(steps.toList, initialMarking, currentMarking)
      }

      if (!net.isEnabled(transition, currentMarking)) {
        println(s"  [Step ${idx + 1}] ERROR: Transition '${transition.id}' is not enabled")
        return SimulationTrace(steps.toList, initialMarking, currentMarking)
      }

      val newMarking = net.fire(transition, currentMarking).get
      steps += SimulationStep(idx + 1, transition, currentMarking, newMarking)
      currentMarking = newMarking
    }

    SimulationTrace(steps.toList, initialMarking, currentMarking)
  }

  /** Print a simulation trace */
  def printTrace(trace: SimulationTrace, net: PetriNet): Unit = {
    println(s"\n${"=" * 60}")
    println("PETRI NET SIMULATION TRACE")
    println(s"${"=" * 60}")
    println(s"Total steps: ${trace.steps.size}")
    println(s"\nInitial marking:")
    println(formatNonZero(trace.initialMarking))

    trace.steps.foreach { step =>
      println(s"\n--- Step ${step.stepNumber}: Fire ${step.transition.id} ---")
      println(s"  Result: ${formatNonZero(step.markingAfter)}")
    }

    println(s"\nFinal marking:")
    println(formatNonZero(trace.finalMarking))
    println(s"${"=" * 60}")
  }

  /** Run predefined scenarios for the order system */
  def runOrderSystemScenarios(net: PetriNet): Unit = {
    println(s"\n${"=" * 60}")
    println("PETRI NET SCENARIO SIMULATIONS")
    println(s"${"=" * 60}")

    // Scenario 1: Happy path - order placed, inventory ok, payment success
    println("\n--- Scenario 1: Happy Path ---")
    val happyPath = simulateSequence(net, OrderSystemPetriNet.initialMarking(), List(
      "T_place_order",
      "T_inventory_ok",
      "T_process_payment",
      "T_payment_ok"
    ))
    printTrace(happyPath, net)

    // Scenario 2: Inventory failure
    println("\n--- Scenario 2: Inventory Failure ---")
    val invFail = simulateSequence(net, OrderSystemPetriNet.initialMarking(), List(
      "T_place_order",
      "T_inventory_fail"
    ))
    printTrace(invFail, net)

    // Scenario 3: Payment failure with inventory restore
    println("\n--- Scenario 3: Payment Failure ---")
    val payFail = simulateSequence(net, OrderSystemPetriNet.initialMarking(), List(
      "T_place_order",
      "T_inventory_ok",
      "T_process_payment",
      "T_payment_fail",
      "T_restore_inv_fail"
    ))
    printTrace(payFail, net)

    // Scenario 4: Inventory communication failure
    println("\n--- Scenario 4: Communication Failure ---")
    val commFail = simulateSequence(net, OrderSystemPetriNet.initialMarking(), List(
      "T_place_order",
      "T_inv_comm_fail"
    ))
    printTrace(commFail, net)

    // Scenario 5: Payment timeout with inventory restore
    println("\n--- Scenario 5: Payment Timeout ---")
    val payTimeout = simulateSequence(net, OrderSystemPetriNet.initialMarking(), List(
      "T_place_order",
      "T_inventory_ok",
      "T_process_payment",
      "T_pay_timeout",
      "T_restore_inv_timeout"
    ))
    printTrace(payTimeout, net)

    // Scenario 6: Random simulation
    println("\n--- Scenario 6: Random Simulation (20 steps) ---")
    val randomSim = simulate(net, OrderSystemPetriNet.initialMarking(), maxSteps = 20, seed = 42)
    printTrace(randomSim, net)
  }

  private def formatNonZero(m: Marking): String =
    m.filter(_._2 > 0).toList.sortBy(_._1.id).map { case (p, n) =>
      s"  ${p.id} = $n"
    }.mkString("\n")
}
