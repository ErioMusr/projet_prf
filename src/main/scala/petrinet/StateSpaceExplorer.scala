package petrinet

import scala.collection.mutable

/**
 * State space explorer: generates the reachability graph from an initial marking.
 * Uses BFS to enumerate all reachable markings and transitions between them.
 */
case class StateNode(marking: Marking, id: Int)
case class StateEdge(from: Int, to: Int, transition: Transition)

case class ReachabilityGraph(
  nodes: List[StateNode],
  edges: List[StateEdge],
  initialNodeId: Int
) {
  def nodeCount: Int = nodes.size
  def edgeCount: Int = edges.size

  /** Get all successor node IDs from a given node */
  def successors(nodeId: Int): List[(Int, Transition)] =
    edges.filter(_.from == nodeId).map(e => (e.to, e.transition))

  /** Get all predecessor node IDs of a given node */
  def predecessors(nodeId: Int): List[(Int, Transition)] =
    edges.filter(_.to == nodeId).map(e => (e.from, e.transition))

  /** Find all deadlock states (nodes with no outgoing edges) */
  def deadlockStates: List[StateNode] = {
    val nodesWithOutgoing = edges.map(_.from).toSet
    nodes.filterNot(n => nodesWithOutgoing.contains(n.id))
  }

  /** Find all terminal states (no successors) */
  def terminalStates: List[StateNode] = deadlockStates
}

object StateSpaceExplorer {

  /**
   * Explore the full state space using BFS.
   * @param net the Petri net
   * @param initialMarking the starting marking
   * @param maxStates safety limit to prevent state explosion
   * @return the reachability graph
   */
  def explore(net: PetriNet, initialMarking: Marking, maxStates: Int = 100000): ReachabilityGraph = {
    val visited = mutable.Map[Marking, Int]()
    val nodes = mutable.ListBuffer[StateNode]()
    val edges = mutable.ListBuffer[StateEdge]()
    val queue = mutable.Queue[Marking]()

    var nextId = 0

    // Normalize marking: only keep places with non-zero tokens for comparison
    def normalize(m: Marking): Marking = m.filter(_._2 != 0)

    val normalizedInitial = normalize(initialMarking)
    visited(normalizedInitial) = nextId
    nodes += StateNode(initialMarking, nextId)
    queue.enqueue(initialMarking)
    nextId += 1

    while (queue.nonEmpty && nextId < maxStates) {
      val currentMarking = queue.dequeue()
      val currentId = visited(normalize(currentMarking))

      val enabled = net.enabledTransitions(currentMarking)

      for (t <- enabled) {
        net.fire(t, currentMarking) match {
          case Some(newMarking) =>
            val normalizedNew = normalize(newMarking)
            val targetId = visited.getOrElseUpdate(normalizedNew, {
              val id = nextId
              nextId += 1
              nodes += StateNode(newMarking, id)
              queue.enqueue(newMarking)
              id
            })
            edges += StateEdge(currentId, targetId, t)
          case None => // should not happen since we checked isEnabled
        }
      }
    }

    ReachabilityGraph(nodes.toList, edges.toList, 0)
  }

  /** Print a summary of the reachability graph */
  def printSummary(graph: ReachabilityGraph, net: PetriNet): Unit = {
    println(s"\n${"=" * 60}")
    println("STATE SPACE EXPLORATION RESULTS")
    println(s"${"=" * 60}")
    println(s"Total reachable states: ${graph.nodeCount}")
    println(s"Total transitions fired: ${graph.edgeCount}")

    val deadlocks = graph.deadlockStates
    if (deadlocks.isEmpty) {
      println("Deadlock states: NONE (deadlock-free)")
    } else {
      println(s"Deadlock states: ${deadlocks.size}")
      deadlocks.foreach { node =>
        println(s"  State #${node.id}:")
        println(net.printMarking(node.marking))
      }
    }
    println(s"${"=" * 60}")
  }
}
