package petrinet

/**
 * Application-specific Petri net model for the Order Management System.
 * Matches the Petri net diagram in scala.png exactly:
 *   6 Places (P1-P6), 10 Transitions
 *   Initial marking M0 = (1, 0, 0, 0, 0, 0)
 */
object OrderSystemPetriNet {

  // ===== Places (matching diagram P1-P6) =====
  val P1 = Place("P1_Idle")               // Idle/Login - system ready
  val P2 = Place("P2_Order_Pending")      // Order placed, awaiting inventory check
  val P3 = Place("P3_Order_Reserved")     // Inventory confirmed, stock reserved
  val P4 = Place("P4_Payment_Processing") // Payment being processed
  val P5 = Place("P5_Order_Confirmed")    // Order completed successfully
  val P6 = Place("P6_Fail")              // Failure state (any failure)

  // ===== Transitions (matching diagram) =====
  val T_PlaceOrder             = Transition("T1_PlaceOrder")
  val T_Inv_Enough             = Transition("T2_Inv_Enough_Reduce_Success")
  val T_Inv_NotFound           = Transition("T3_Inv_NotFound")
  val T_Inv_Insufficient       = Transition("T4_Inv_Insufficient")
  val T_Check_Inv_Timeout      = Transition("T5_Check_Inv_Timeout")
  val T_Submit_Payment         = Transition("T6_Submit_Payment")
  val T_Payment_Success        = Transition("T7_Payment_Success")
  val T_Payment_Failed         = Transition("T8_Payment_Failed")
  val T_Payment_Timeout        = Transition("T9_Payment_Timeout")
  val T_Fail_Recovery          = Transition("T10_Fail_Recovery")  // P6 -> P1 (back to idle)

  val allPlaces: Set[Place] = Set(P1, P2, P3, P4, P5, P6)

  val allTransitions: Set[Transition] = Set(
    T_PlaceOrder, T_Inv_Enough, T_Inv_NotFound, T_Inv_Insufficient,
    T_Check_Inv_Timeout, T_Submit_Payment, T_Payment_Success,
    T_Payment_Failed, T_Payment_Timeout, T_Fail_Recovery
  )

  // ===== Arcs (matching diagram flow) =====

  // Input arcs: Place -> Transition
  val inputArcs: Set[Arc] = Set(
    // P1 -> T_PlaceOrder
    Arc(P1.id, T_PlaceOrder.id),

    // P2 -> inventory check outcomes
    Arc(P2.id, T_Inv_Enough.id),
    Arc(P2.id, T_Inv_NotFound.id),
    Arc(P2.id, T_Inv_Insufficient.id),
    Arc(P2.id, T_Check_Inv_Timeout.id),

    // P3 -> T_Submit_Payment
    Arc(P3.id, T_Submit_Payment.id),

    // P4 -> payment outcomes
    Arc(P4.id, T_Payment_Success.id),
    Arc(P4.id, T_Payment_Failed.id),
    Arc(P4.id, T_Payment_Timeout.id),

    // P6 -> T_Fail_Recovery (Fail returns to Idle)
    Arc(P6.id, T_Fail_Recovery.id)
  )

  // Output arcs: Transition -> Place
  val outputArcs: Set[Arc] = Set(
    // T_PlaceOrder -> P2
    Arc(T_PlaceOrder.id, P2.id),

    // T_Inv_Enough -> P3 (inventory ok, reserve stock)
    Arc(T_Inv_Enough.id, P3.id),

    // Inventory failures -> P6 (Fail)
    Arc(T_Inv_NotFound.id, P6.id),
    Arc(T_Inv_Insufficient.id, P6.id),
    Arc(T_Check_Inv_Timeout.id, P6.id),

    // T_Submit_Payment -> P4
    Arc(T_Submit_Payment.id, P4.id),

    // T_Payment_Success -> P5 (Order Confirmed)
    Arc(T_Payment_Success.id, P5.id),

    // Payment failures -> P6 (Fail)
    Arc(T_Payment_Failed.id, P6.id),
    Arc(T_Payment_Timeout.id, P6.id),

    // T_Fail_Recovery -> P1 (back to Idle)
    Arc(T_Fail_Recovery.id, P1.id)
  )

  /** Build the complete Petri net */
  def build(): PetriNet = PetriNet(allPlaces, allTransitions, inputArcs, outputArcs)

  /**
   * Initial marking M0 = (1, 0, 0, 0, 0, 0)
   * Only P1 (Idle) has a token.
   */
  def initialMarking(): Marking = Map(
    P1 -> 1,
    P2 -> 0,
    P3 -> 0,
    P4 -> 0,
    P5 -> 0,
    P6 -> 0
  )

  /** Pretty label for the marking vector notation */
  def markingVector(m: Marking): String = {
    val values = List(P1, P2, P3, P4, P5, P6).map(p => m.getOrElse(p, 0))
    s"M(${values.mkString(",")})"
  }
}
