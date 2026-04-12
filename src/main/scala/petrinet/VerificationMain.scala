package petrinet

/**
 * Main entry point for Petri net verification and analysis.
 * Matches the diagram: 6 Places (P1-P6), 10 Transitions.
 */
object VerificationMain {

  def main(args: Array[String]): Unit = {
    println("=" * 60)
    println("PETRI NET VERIFICATION SUITE")
    println("Order Management System - Formal Analysis")
    println("=" * 60)

    // Step 1: Build the Petri net (matching scala.png diagram)
    println("\n[1/5] Building Petri net model...")
    val net = OrderSystemPetriNet.build()
    val m0 = OrderSystemPetriNet.initialMarking()
    println(s"  Places: ${net.places.size} (P1-P6)")
    println(s"  Transitions: ${net.transitions.size} (T1-T10)")
    println(s"  Input arcs: ${net.inputArcs.size}")
    println(s"  Output arcs: ${net.outputArcs.size}")
    println(s"  Initial marking: ${OrderSystemPetriNet.markingVector(m0)}")

    // Step 2: Explore state space
    println("\n[2/5] Exploring state space (BFS)...")
    val graph = StateSpaceExplorer.explore(net, m0)
    StateSpaceExplorer.printSummary(graph, net)

    // Step 3: Structural property analysis
    println("\n[3/5] Analyzing structural properties and invariants...")
    PropertyAnalyzer.runFullAnalysis(net, graph)

    // Step 4: LTL model checking
    println("\n[4/5] Running LTL model checking...")
    LTLChecker.runOrderSystemChecks(graph)

    // Step 5: Scenario simulation
    println("\n[5/5] Running scenario simulations...")
    PetriNetSimulator.runOrderSystemScenarios(net)

    // Step 6: Comparative analysis
    println("\n[BONUS] Comparative simulation with Akka traces...")
    SimulationComparator.runComparison(net, m0, "log.txt")

    println("\n" + "=" * 60)
    println("VERIFICATION COMPLETE")
    println("=" * 60)
  }
}
