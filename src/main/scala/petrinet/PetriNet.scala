package petrinet

/**
 * Core Petri Net data structures and firing semantics.
 * Hand-coded implementation (no external Petri net tools).
 */

case class Place(id: String)
case class Transition(id: String)
case class Arc(from: String, to: String, weight: Int = 1)

case class PetriNet(
  places: Set[Place],
  transitions: Set[Transition],
  inputArcs: Set[Arc],   // Place -> Transition
  outputArcs: Set[Arc]   // Transition -> Place
) {

  /** Get input places and their arc weights for a transition */
  def inputPlaces(t: Transition): Map[Place, Int] =
    inputArcs.filter(_.to == t.id).map { arc =>
      Place(arc.from) -> arc.weight
    }.toMap

  /** Get output places and their arc weights for a transition */
  def outputPlaces(t: Transition): Map[Place, Int] =
    outputArcs.filter(_.from == t.id).map { arc =>
      Place(arc.to) -> arc.weight
    }.toMap

  /** Check if a transition is enabled (all input places have enough tokens) */
  def isEnabled(t: Transition, m: Marking): Boolean =
    inputPlaces(t).forall { case (place, weight) =>
      m.getOrElse(place, 0) >= weight
    }

  /** Get all transitions enabled in a given marking */
  def enabledTransitions(m: Marking): Set[Transition] =
    transitions.filter(t => isEnabled(t, m))

  /** Fire a transition: consume input tokens, produce output tokens */
  def fire(t: Transition, m: Marking): Option[Marking] = {
    if (!isEnabled(t, m)) return None

    var newMarking = m

    // Consume tokens from input places
    inputPlaces(t).foreach { case (place, weight) =>
      val current = newMarking.getOrElse(place, 0)
      newMarking = newMarking + (place -> (current - weight))
    }

    // Produce tokens in output places
    outputPlaces(t).foreach { case (place, weight) =>
      val current = newMarking.getOrElse(place, 0)
      newMarking = newMarking + (place -> (current + weight))
    }

    Some(newMarking)
  }

  /** Build the incidence matrix C where C[p][t] = output_weight - input_weight */
  def incidenceMatrix: Map[Place, Map[Transition, Int]] = {
    val placeList = places.toList.sortBy(_.id)
    val transList = transitions.toList.sortBy(_.id)

    placeList.map { p =>
      p -> transList.map { t =>
        val inWeight = inputArcs.find(a => a.from == p.id && a.to == t.id).map(_.weight).getOrElse(0)
        val outWeight = outputArcs.find(a => a.from == t.id && a.to == p.id).map(_.weight).getOrElse(0)
        t -> (outWeight - inWeight)
      }.toMap
    }.toMap
  }

  /** Check if a marking has a deadlock (no transitions enabled) */
  def isDeadlock(m: Marking): Boolean =
    enabledTransitions(m).isEmpty

  /** Pretty-print a marking */
  def printMarking(m: Marking): String =
    places.toList.sortBy(_.id).map { p =>
      s"  ${p.id}: ${m.getOrElse(p, 0)}"
    }.mkString("\n")
}
