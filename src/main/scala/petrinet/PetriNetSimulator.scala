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

  def simulateSequence(net: PetriNet, initialMarking: Marking, transitionIds: List[String]): SimulationTrace = {
    val steps = mutable.ListBuffer[SimulationStep]()
    var currentMarking = initialMarking

    for ((tId, idx) <- transitionIds.zipWithIndex) {
      val transition = net.transitions.find(_.id == tId) match {
        case Some(t) => t
        case None =>
          println(s"  [Step ${idx + 1}] ERROR: Transition '$tId' not found")
          return SimulationTrace(steps.toList, initialMarking, currentMarking)
      }
      if (!net.isEnabled(transition, currentMarking)) {
        println(s"  [Step ${idx + 1}] ERROR: Transition '${transition.id}' is not enabled")
        println(s"    Current marking: ${OrderSystemPetriNet.markingVector(currentMarking)}")
        return SimulationTrace(steps.toList, initialMarking, currentMarking)
      }
      val newMarking = net.fire(transition, currentMarking).get
      steps += SimulationStep(idx + 1, transition, currentMarking, newMarking)
      currentMarking = newMarking
    }
    SimulationTrace(steps.toList, initialMarking, currentMarking)
  }

  def printTrace(trace: SimulationTrace, net: PetriNet): Unit = {
    println(s"  Total steps: ${trace.steps.size}")
    println(s"  Initial: ${OrderSystemPetriNet.markingVector(trace.initialMarking)}")

    trace.steps.foreach { step =>
      println(s"  Step ${step.stepNumber}: ${step.transition.id}")
      println(s"    -> ${OrderSystemPetriNet.markingVector(step.markingAfter)}")
    }

    println(s"  Final:   ${OrderSystemPetriNet.markingVector(trace.finalMarking)}")
  }

  /** Run predefined scenarios matching the diagram */
  def runOrderSystemScenarios(net: PetriNet): Unit = {
    println(s"\n${"=" * 60}")
    println("PETRI NET SCENARIO SIMULATIONS")
    println(s"${"=" * 60}")

    val m0 = OrderSystemPetriNet.initialMarking()

    // Scenario 1: Happy Path — PlaceOrder -> InvOK -> Payment -> Success
    println("\n--- Scenario 1: Happy Path (P1->P2->P3->P4->P5) ---")
    val s1 = simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T5_Inv_Success",
      "T7_Submit_Pay",
      "T9_Pay_Success"
    ))
    printTrace(s1, net)

    // Scenario 2: Inventory Not Found (P1->P2->P6)
    println("\n--- Scenario 2: Inventory Not Found ---")
    val s2 = simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T2_Inv_NotFound"
    ))
    printTrace(s2, net)

    // Scenario 3: Inventory Insufficient (P1->P2->P6)
    println("\n--- Scenario 3: Inventory Insufficient ---")
    val s3 = simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T4_Inv_Insufficient"
    ))
    printTrace(s3, net)

    // Scenario 4: Payment Failed (P1->P2->P3->P4->P6)
    println("\n--- Scenario 4: Payment Failed ---")
    val s4 = simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T5_Inv_Success",
      "T7_Submit_Pay",
      "T8_Pay_Failed"
    ))
    printTrace(s4, net)

    // Scenario 5: Inventory Timeout (P1->P2->P6)
    println("\n--- Scenario 5: Inventory Check Timeout ---")
    val s5 = simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T3_Check_Timeout"
    ))
    printTrace(s5, net)

    // Scenario 6: Payment Timeout (P1->P2->P3->P6)
    println("\n--- Scenario 6: Payment Timeout ---")
    val s6 = simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T5_Inv_Success",
      "T6_Pay_Timeout"
    ))
    printTrace(s6, net)

    // Scenario 7: Random simulation (20 steps)
    println("\n--- Scenario 7: Random Simulation (20 steps) ---")
    val s7 = simulate(net, m0, maxSteps = 20, seed = 42)
    printTrace(s7, net)
  }
}
