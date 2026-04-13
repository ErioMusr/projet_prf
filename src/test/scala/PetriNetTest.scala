import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import petrinet._

class PetriNetTest extends AnyFlatSpec with Matchers {

  val net = OrderSystemPetriNet.build()
  val m0 = OrderSystemPetriNet.initialMarking()

  "PetriNet" should "have 6 places and 9 transitions matching the diagram" in {
    net.places.size shouldBe 6
    net.transitions.size shouldBe 9
  }

  it should "only enable T1_PlaceOrder in initial marking M(1,0,0,0,0,0)" in {
    val enabled = net.enabledTransitions(m0)
    enabled.map(_.id) should contain("T1_PlaceOrder")
    enabled.size shouldBe 1
  }

  it should "move token from P1 to P2 after T1_PlaceOrder" in {
    val m1 = net.fire(net.transitions.find(_.id == "T1_PlaceOrder").get, m0).get
    m1(OrderSystemPetriNet.P1) shouldBe 0
    m1(OrderSystemPetriNet.P2) shouldBe 1
    OrderSystemPetriNet.markingVector(m1) shouldBe "M(0,1,0,0,0,0)"
  }

  it should "not fire a disabled transition" in {
    net.fire(net.transitions.find(_.id == "T9_Pay_Success").get, m0) shouldBe None
  }

  "StateSpaceExplorer" should "find exactly 6 reachable states for M(1,0,0,0,0,0)" in {
    val graph = StateSpaceExplorer.explore(net, m0)
    // P1, P2, P3, P4, P5, P6 — one token moves through 6 places
    graph.nodeCount shouldBe 6
  }

  "PropertyAnalyzer" should "verify token conservation (total = 1)" in {
    val graph = StateSpaceExplorer.explore(net, m0)
    val result = PropertyAnalyzer.checkTokenConservation(graph)
    result.satisfied shouldBe true
  }

  it should "verify order state mutual exclusion" in {
    val graph = StateSpaceExplorer.explore(net, m0)
    val result = PropertyAnalyzer.checkOrderStateMutualExclusion(graph)
    result.satisfied shouldBe true
  }

  it should "check boundedness as 1-bounded" in {
    val graph = StateSpaceExplorer.explore(net, m0)
    val result = PropertyAnalyzer.checkBoundedness(net, graph)
    result.satisfied shouldBe true
    result.details should include("1-bounded")
  }

  "LTLChecker" should "verify G(single_token) — token conservation via LTL" in {
    val graph = StateSpaceExplorer.explore(net, m0)
    val result = LTLChecker.check(Globally(LTLChecker.singleToken), graph)
    result.satisfied shouldBe true
  }

  it should "verify F(idle) — system can return to idle" in {
    val graph = StateSpaceExplorer.explore(net, m0)
    val result = LTLChecker.check(Eventually(LTLChecker.inP1_Idle), graph)
    result.satisfied shouldBe true
  }

  "PetriNetSimulator" should "complete happy path P1->P2->P3->P4->P5" in {
    val trace = PetriNetSimulator.simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T5_Inv_Success",
      "T7_Submit_Pay",
      "T9_Pay_Success"
    ))
    trace.steps.size shouldBe 4
    trace.finalMarking(OrderSystemPetriNet.P5) shouldBe 1
    OrderSystemPetriNet.markingVector(trace.finalMarking) shouldBe "M(0,0,0,0,1,0)"
  }

  it should "complete failure path P1->P2->P6" in {
    val trace = PetriNetSimulator.simulateSequence(net, m0, List(
      "T1_PlaceOrder",
      "T2_Inv_NotFound"
    ))
    trace.steps.size shouldBe 2
    trace.finalMarking(OrderSystemPetriNet.P6) shouldBe 1
    OrderSystemPetriNet.markingVector(trace.finalMarking) shouldBe "M(0,0,0,0,0,1)"
  }

  "SimulationComparator" should "group events by order and validate independently" in {
    val events = List(
      SimulationComparator.AkkaEvent(1000L, "PlaceOrder", List("product-1", "1")),
      SimulationComparator.AkkaEvent(1001L, "Check_Inv_Start", List("order-1", "product-1", "1")),
      SimulationComparator.AkkaEvent(1002L, "Inv_Enough_Reduce_Success", List("order-1", "product-1", "1", "9")),
      SimulationComparator.AkkaEvent(1003L, "Submit_Payment", List("order-1", "product-1", "10.0", "10.0")),
      SimulationComparator.AkkaEvent(1004L, "Payment_Success", List("order-1", "product-1", "10.0")),
      SimulationComparator.AkkaEvent(2000L, "PlaceOrder", List("product-2", "2")),
      SimulationComparator.AkkaEvent(2001L, "Check_Inv_Start", List("order-2", "product-2", "2")),
      SimulationComparator.AkkaEvent(2002L, "Inv_NotFound", List("order-2", "product-2"))
    )

    val grouped = SimulationComparator.groupByOrder(events)
    grouped.keySet shouldBe Set("order-1", "order-2")
    grouped("order-1").head.eventType shouldBe "PlaceOrder"
    grouped("order-2").head.eventType shouldBe "PlaceOrder"

    val result = SimulationComparator.validateTraceByOrder(net, m0, events)
    result.groupedOrders shouldBe 2
    result.summary.invalidTransitions shouldBe 0
    result.summary.validTransitions shouldBe 6
    result.summary.skippedMappedEvents shouldBe 0
  }
}
