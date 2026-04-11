package petrinet

/**
 * Main entry point for Petri net verification and analysis.
 * Runs state space exploration, property analysis, LTL model checking,
 * scenario simulation, and comparative analysis with the Akka system.
 */
object VerificationMain {

  def main(args: Array[String]): Unit = {
    println("=" * 60)
    println("PETRI NET VERIFICATION SUITE")
    println("Order Management System - Formal Analysis")
    println("=" * 60)

    // Step 1: Build the Petri net
    println("\n[1/5] Building Petri net model...")
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking(inventoryStock = 5)
    println(s"  Places: ${net.places.size}")
    println(s"  Transitions: ${net.transitions.size}")
    println(s"  Input arcs: ${net.inputArcs.size}")
    println(s"  Output arcs: ${net.outputArcs.size}")
    println(s"\n  Initial marking:")
    println(net.printMarking(initial))

    // Step 2: Explore state space
    println("\n[2/5] Exploring state space...")
    val graph = StateSpaceExplorer.explore(net, initial)
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

    // Step 6: Comparative analysis (if log.txt exists)
    println("\n[BONUS] Comparative simulation with Akka traces...")
    SimulationComparator.runComparison(net, initial, "log.txt")

    println("\n" + "=" * 60)
    println("VERIFICATION COMPLETE")
    println("=" * 60)
  }
}
