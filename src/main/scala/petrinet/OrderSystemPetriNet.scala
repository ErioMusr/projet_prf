package petrinet

/**
 * Application-specific Petri net model for the Order Management System.
 * Maps Akka actor states to places and message exchanges to transitions.
 */
object OrderSystemPetriNet {

  // ===== Places =====
  // Order lifecycle places
  val P_idle              = Place("P_idle")               // System ready to accept orders
  val P_pending_inventory = Place("P_pending_inventory")  // Order waiting for inventory check
  val P_stock_reserved    = Place("P_stock_reserved")     // Inventory confirmed, awaiting payment
  val P_payment_processing = Place("P_payment_processing") // Payment being processed
  val P_payment_success   = Place("P_payment_success")    // Payment completed successfully
  val P_payment_failed    = Place("P_payment_failed")     // Payment failed

  // Inventory places
  val P_inventory         = Place("P_inventory")          // Available inventory (tokens = stock units)

  // Communication failure / timeout places
  val P_inv_timeout       = Place("P_inv_timeout")        // Inventory check timed out
  val P_pay_timeout       = Place("P_pay_timeout")        // Payment timed out

  // Actor readiness places
  val P_inv_actor_ready   = Place("P_inv_actor_ready")    // InventoryActor ready to process
  val P_pay_actor_ready   = Place("P_pay_actor_ready")    // PaymentActor ready to process

  // ===== Transitions =====
  val T_place_order       = Transition("T_place_order")         // User places an order
  val T_check_inventory   = Transition("T_check_inventory")     // Send inventory check request
  val T_inventory_ok      = Transition("T_inventory_ok")        // Inventory sufficient, reserve stock
  val T_inventory_fail    = Transition("T_inventory_fail")      // Inventory insufficient
  val T_item_not_found    = Transition("T_item_not_found")      // Product not found
  val T_inv_comm_fail     = Transition("T_inv_comm_fail")       // Inventory communication failure
  val T_process_payment   = Transition("T_process_payment")     // Initiate payment
  val T_payment_ok        = Transition("T_payment_ok")          // Payment succeeded
  val T_payment_fail      = Transition("T_payment_fail")        // Payment failed (gateway declined)
  val T_pay_comm_fail     = Transition("T_pay_comm_fail")       // Payment communication failure
  val T_inv_timeout       = Transition("T_inv_timeout")         // Inventory check timeout
  val T_pay_timeout       = Transition("T_pay_timeout")         // Payment timeout
  val T_restore_inv_fail  = Transition("T_restore_inv_fail")    // Restore inventory after payment failure
  val T_restore_inv_timeout = Transition("T_restore_inv_timeout") // Restore inventory after timeout

  val allPlaces: Set[Place] = Set(
    P_idle, P_pending_inventory, P_stock_reserved, P_payment_processing,
    P_payment_success, P_payment_failed, P_inventory,
    P_inv_timeout, P_pay_timeout,
    P_inv_actor_ready, P_pay_actor_ready
  )

  val allTransitions: Set[Transition] = Set(
    T_place_order, T_check_inventory, T_inventory_ok, T_inventory_fail,
    T_item_not_found, T_inv_comm_fail, T_process_payment, T_payment_ok,
    T_payment_fail, T_pay_comm_fail, T_inv_timeout, T_pay_timeout,
    T_restore_inv_fail, T_restore_inv_timeout
  )

  // ===== Arcs =====
  // Input arcs: Place -> Transition
  val inputArcs: Set[Arc] = Set(
    // T_place_order: consume idle token
    Arc(P_idle.id, T_place_order.id),

    // T_check_inventory: order is pending, inventory actor ready
    Arc(P_pending_inventory.id, T_check_inventory.id),
    Arc(P_inv_actor_ready.id, T_check_inventory.id),

    // T_inventory_ok: pending inventory check + stock available
    Arc(P_pending_inventory.id, T_inventory_ok.id),
    Arc(P_inventory.id, T_inventory_ok.id),

    // T_inventory_fail: pending inventory check (not enough stock)
    Arc(P_pending_inventory.id, T_inventory_fail.id),

    // T_item_not_found: pending inventory check (product doesn't exist)
    Arc(P_pending_inventory.id, T_item_not_found.id),

    // T_inv_comm_fail: pending inventory check (communication error)
    Arc(P_pending_inventory.id, T_inv_comm_fail.id),

    // T_inv_timeout: pending inventory check exceeds timeout
    Arc(P_pending_inventory.id, T_inv_timeout.id),

    // T_process_payment: stock reserved, payment actor ready
    Arc(P_stock_reserved.id, T_process_payment.id),
    Arc(P_pay_actor_ready.id, T_process_payment.id),

    // T_payment_ok: payment processing succeeds
    Arc(P_payment_processing.id, T_payment_ok.id),

    // T_payment_fail: payment processing fails
    Arc(P_payment_processing.id, T_payment_fail.id),

    // T_pay_comm_fail: payment communication failure
    Arc(P_payment_processing.id, T_pay_comm_fail.id),

    // T_pay_timeout: payment processing exceeds timeout
    Arc(P_payment_processing.id, T_pay_timeout.id),

    // T_restore_inv_fail: restore inventory after payment failure
    Arc(P_payment_failed.id, T_restore_inv_fail.id),

    // T_restore_inv_timeout: restore inventory after payment timeout
    Arc(P_pay_timeout.id, T_restore_inv_timeout.id)
  )

  // Output arcs: Transition -> Place
  val outputArcs: Set[Arc] = Set(
    // T_place_order -> pending inventory check
    Arc(T_place_order.id, P_pending_inventory.id),

    // T_check_inventory -> pending inventory (actor processes the check)
    Arc(T_check_inventory.id, P_pending_inventory.id),
    Arc(T_check_inventory.id, P_inv_actor_ready.id),

    // T_inventory_ok -> stock reserved
    Arc(T_inventory_ok.id, P_stock_reserved.id),

    // T_inventory_fail -> idle (order cancelled, return to ready state)
    Arc(T_inventory_fail.id, P_idle.id),

    // T_item_not_found -> idle
    Arc(T_item_not_found.id, P_idle.id),

    // T_inv_comm_fail -> idle (order cancelled due to comm failure)
    Arc(T_inv_comm_fail.id, P_idle.id),

    // T_inv_timeout -> idle
    Arc(T_inv_timeout.id, P_idle.id),

    // T_process_payment -> payment processing
    Arc(T_process_payment.id, P_payment_processing.id),

    // T_payment_ok -> payment success + idle (system ready for next order)
    Arc(T_payment_ok.id, P_payment_success.id),
    Arc(T_payment_ok.id, P_idle.id),
    Arc(T_payment_ok.id, P_pay_actor_ready.id),

    // T_payment_fail -> payment failed
    Arc(T_payment_fail.id, P_payment_failed.id),
    Arc(T_payment_fail.id, P_pay_actor_ready.id),

    // T_pay_comm_fail -> payment failed
    Arc(T_pay_comm_fail.id, P_payment_failed.id),
    Arc(T_pay_comm_fail.id, P_pay_actor_ready.id),

    // T_pay_timeout -> payment timeout
    Arc(T_pay_timeout.id, P_pay_timeout.id),
    Arc(T_pay_timeout.id, P_pay_actor_ready.id),

    // T_restore_inv_fail -> restore inventory token + idle
    Arc(T_restore_inv_fail.id, P_inventory.id),
    Arc(T_restore_inv_fail.id, P_idle.id),

    // T_restore_inv_timeout -> restore inventory token + idle
    Arc(T_restore_inv_timeout.id, P_inventory.id),
    Arc(T_restore_inv_timeout.id, P_idle.id)
  )

  /** Build the complete Petri net for the order management system */
  def build(): PetriNet = PetriNet(allPlaces, allTransitions, inputArcs, outputArcs)

  /**
   * Initial marking: system ready with inventory stock.
   * @param inventoryStock number of inventory tokens (default stock units)
   */
  def initialMarking(inventoryStock: Int = 5): Marking = Map(
    P_idle            -> 1,
    P_pending_inventory -> 0,
    P_stock_reserved  -> 0,
    P_payment_processing -> 0,
    P_payment_success -> 0,
    P_payment_failed  -> 0,
    P_inventory       -> inventoryStock,
    P_inv_timeout     -> 0,
    P_pay_timeout     -> 0,
    P_inv_actor_ready -> 1,
    P_pay_actor_ready -> 1
  )
}
