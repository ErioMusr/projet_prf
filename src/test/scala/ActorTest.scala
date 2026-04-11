import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.SupervisorStrategy
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class ActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {

  "InventoryActor" should {
    "respond to GetInventory with current stock" in {
      val inventoryActor = spawn(InventoryActor())
      val probe = createTestProbe[InventoryQueryResponse]()

      inventoryActor ! GetInventory(probe.ref)
      val response = probe.receiveMessage()

      response shouldBe a[InventoryQueryResult]
      val result = response.asInstanceOf[InventoryQueryResult]
      result.items should not be empty
    }

    "update inventory when UpdateInventory is sent" in {
      val inventoryActor = spawn(InventoryActor())
      val updateProbe = createTestProbe[InventoryUpdateResponse]()
      val queryProbe = createTestProbe[InventoryQueryResponse]()

      inventoryActor ! UpdateInventory("test-product", 100, updateProbe.ref)
      val updateResult = updateProbe.receiveMessage()
      updateResult shouldBe a[InventoryUpdateSuccess]

      inventoryActor ! GetInventory(queryProbe.ref)
      val queryResult = queryProbe.receiveMessage().asInstanceOf[InventoryQueryResult]
      queryResult.items("test-product") shouldBe 100
    }

    "restore inventory correctly" in {
      val inventoryActor = spawn(InventoryActor())
      val queryProbe = createTestProbe[InventoryQueryResponse]()

      // Get initial state
      inventoryActor ! GetInventory(queryProbe.ref)
      val initial = queryProbe.receiveMessage().asInstanceOf[InventoryQueryResult]
      val initialQty = initial.items.getOrElse("product-1", 0)

      // Restore 5 units
      inventoryActor ! RestoreInventory("product-1", 5)

      // Small delay for message processing
      Thread.sleep(200)

      inventoryActor ! GetInventory(queryProbe.ref)
      val after = queryProbe.receiveMessage().asInstanceOf[InventoryQueryResult]
      after.items("product-1") shouldBe (initialQty + 5)
    }
  }

  "PaymentActor" should {
    "respond to GetAllPayments" in {
      val paymentActor = spawn(PaymentActor())
      val probe = createTestProbe[AllPaymentsResponse]()

      paymentActor ! GetAllPayments(probe.ref)
      val response = probe.receiveMessage()
      response shouldBe a[AllPaymentsResponse]
    }
  }

  "OrderActor" should {
    "respond to GetAllOrders" in {
      val inventoryActor = spawn(InventoryActor())
      val paymentActor = spawn(PaymentActor())
      val orderActor = spawn(OrderActor(inventoryActor, paymentActor))
      val probe = createTestProbe[AllOrdersResponse]()

      orderActor ! GetAllOrders(probe.ref)
      val response = probe.receiveMessage()
      response shouldBe a[AllOrdersResponse]
    }

    "report OrderNotFound for non-existent order" in {
      val inventoryActor = spawn(InventoryActor())
      val paymentActor = spawn(PaymentActor())
      val orderActor = spawn(OrderActor(inventoryActor, paymentActor))
      val probe = createTestProbe[OrderStatusResponse]()

      orderActor ! GetOrderStatus("non-existent-order", probe.ref)
      val response = probe.receiveMessage()
      response shouldBe a[OrderNotFound]
    }

    "handle CancelAllUnpaidOrders gracefully" in {
      val inventoryActor = spawn(InventoryActor())
      val paymentActor = spawn(PaymentActor())
      val orderActor = spawn(OrderActor(inventoryActor, paymentActor))
      val probe = createTestProbe[ShutdownComplete]()

      orderActor ! CancelAllUnpaidOrders(probe.ref)
      val response = probe.receiveMessage()
      response shouldBe a[ShutdownComplete]
    }
  }

  "Supervision" should {
    "restart actors on failure" in {
      val supervisedInventory = spawn(
        Behaviors.supervise(InventoryActor()).onFailure(SupervisorStrategy.restart)
      )
      val probe = createTestProbe[InventoryQueryResponse]()

      supervisedInventory ! GetInventory(probe.ref)
      val response = probe.receiveMessage()
      response shouldBe a[InventoryQueryResult]
    }
  }
}
