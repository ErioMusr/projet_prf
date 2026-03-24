
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

object FileStore {
  val inventoryPath = Paths.get("inventory.txt")
  val orderPath = Paths.get("order.txt")
  val paymentPath = Paths.get("payment.txt")

  // ===== Inventory Operations =====
  def loadInventory(): Map[String, Int] = {
    if (!Files.exists(inventoryPath)) return Map("product-1" -> 10, "product-2" -> 5)
    Files.readAllLines(inventoryPath).asScala.map { line =>
      val Array(id, qty) = line.split(":")
      id -> qty.toInt
    }.toMap
  }

  def saveInventory(items: Map[String, Int]): Unit = {
    val lines = items.map { case (id, qty) => s"$id:$qty" }.toList.asJava
    Files.write(inventoryPath, lines, StandardCharsets.UTF_8)
  }

  // ===== Order Operations =====
  def saveOrder(productId: String, quantity: Int, state: String): Unit = {
    val line = List(s"$productId:$quantity:$state").asJava
    Files.write(orderPath, line, StandardCharsets.UTF_8)
  }

  // ===== Payment Operations =====
  def savePayment(record: PaymentRecord): Unit = {
    val line = s"${record.orderId}:${record.productId}:${record.amount}:${record.status}:${record.timestamp}"
    val existingLines = if (Files.exists(paymentPath)) {
      Files.readAllLines(paymentPath).asScala.toList
    } else {
      List()
    }
    val updatedLines = (existingLines :+ line).asJava
    Files.write(paymentPath, updatedLines, StandardCharsets.UTF_8)
  }

  def loadPayments(): List[PaymentRecord] = {
    if (!Files.exists(paymentPath)) return List()
    Files.readAllLines(paymentPath).asScala.map { line =>
      val parts = line.split(":")
      PaymentRecord(parts(0), parts(1), parts(2).toDouble, parts(3), parts(4).toLong)
    }.toList
  }

}

