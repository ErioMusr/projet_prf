import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import petrinet._

class PetriNetTest extends AnyFlatSpec with Matchers {

  "PetriNet" should "correctly identify enabled transitions" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking()

    val enabled = net.enabledTransitions(initial)
    enabled.map(_.id) should contain("T_place_order")
    enabled.map(_.id) should not contain("T_payment_ok")
  }

  it should "fire T_place_order and move token to P_pending_inventory" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking()

    val afterPlaceOrder = net.fire(
      net.transitions.find(_.id == "T_place_order").get,
      initial
    ).get

    afterPlaceOrder(OrderSystemPetriNet.P_idle) shouldBe 0
    afterPlaceOrder(OrderSystemPetriNet.P_pending_inventory) shouldBe 1
  }

  it should "not fire a transition that is not enabled" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking()

    val result = net.fire(
      net.transitions.find(_.id == "T_payment_ok").get,
      initial
    )

    result shouldBe None
  }

  it should "consume and produce correct token counts for inventory" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking(inventoryStock = 5)

    // Place order
    val m1 = net.fire(net.transitions.find(_.id == "T_place_order").get, initial).get
    // Inventory ok (consumes 1 inventory token)
    val m2 = net.fire(net.transitions.find(_.id == "T_inventory_ok").get, m1).get

    m2(OrderSystemPetriNet.P_inventory) shouldBe 4
    m2(OrderSystemPetriNet.P_stock_reserved) shouldBe 1
  }

  "StateSpaceExplorer" should "generate a non-empty reachability graph" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking(inventoryStock = 2)
    val graph = StateSpaceExplorer.explore(net, initial)

    graph.nodeCount should be > 1
    graph.edgeCount should be > 0
  }

  "PropertyAnalyzer" should "detect no negative inventory in reachable states" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking(inventoryStock = 2)
    val graph = StateSpaceExplorer.explore(net, initial)

    val result = PropertyAnalyzer.checkInventoryNonNegativity(graph, OrderSystemPetriNet.P_inventory)
    result.satisfied shouldBe true
  }

  it should "check boundedness" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking(inventoryStock = 2)
    val graph = StateSpaceExplorer.explore(net, initial)

    val result = PropertyAnalyzer.checkBoundedness(net, graph)
    result.satisfied shouldBe true
  }

  "LTLChecker" should "verify G(inventory >= 0)" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking(inventoryStock = 2)
    val graph = StateSpaceExplorer.explore(net, initial)

    val result = LTLChecker.check(Globally(LTLChecker.inventoryNonNegative), graph)
    result.satisfied shouldBe true
  }

  "PetriNetSimulator" should "run happy path scenario without errors" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking()

    val trace = PetriNetSimulator.simulateSequence(net, initial, List(
      "T_place_order",
      "T_inventory_ok",
      "T_process_payment",
      "T_payment_ok"
    ))

    trace.steps.size shouldBe 4
    trace.finalMarking(OrderSystemPetriNet.P_payment_success) shouldBe 1
  }

  it should "handle payment failure scenario" in {
    val net = OrderSystemPetriNet.build()
    val initial = OrderSystemPetriNet.initialMarking(inventoryStock = 5)

    val trace = PetriNetSimulator.simulateSequence(net, initial, List(
      "T_place_order",
      "T_inventory_ok",
      "T_process_payment",
      "T_payment_fail",
      "T_restore_inv_fail"
    ))

    trace.steps.size shouldBe 5
    // Inventory should be restored
    trace.finalMarking(OrderSystemPetriNet.P_inventory) shouldBe 5
  }
}
