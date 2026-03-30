
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
  def saveOrder(orderId: String, productId: String, quantity: Int, price: Double, paidAmount: Double, state: String): Unit = {
    val line = s"$orderId|$productId|$quantity|$price|$paidAmount|$state|${System.currentTimeMillis()}"
    val existingLines = if (Files.exists(orderPath)) Files.readAllLines(orderPath).asScala.toList else List()
    val filteredLines = existingLines.filterNot(_.startsWith(s"$orderId|"))
    val updatedLines = (filteredLines :+ line).asJava
    Files.write(orderPath, updatedLines, StandardCharsets.UTF_8)
  }

  def loadOrders(): List[(String, String, Int, Double, Double, String, Long)] = {
    if (!Files.exists(orderPath)) return List()
    Files.readAllLines(orderPath).asScala.map { line =>
      val parts = line.split("\\|")
      if (parts.length >= 7) {
        (parts(0), parts(1), parts(2).toInt, parts(3).toDouble, parts(4).toDouble, parts(5), parts(6).toLong)
      } else if (parts.length == 6) {
        (parts(0), parts(1), parts(2).toInt, parts(3).toDouble, 0.0, parts(4), parts(5).toLong)
      } else {
        ("unknown", "unknown", 0, 0.0, 0.0, "unknown", System.currentTimeMillis())
      }
    }.filter(_._1 != "unknown").toList
  }

  def getOrderStatus(orderId: String): Option[(String, String, Int,Double,Double, String, Long)] = {
    loadOrders().find(_._1 == orderId)
  }

  // ===== Payment Operations =====
  def savePayment(record: PaymentRecord): Unit = {
    val line = s"${record.orderId}|${record.productId}|${record.amount}|${record.status}|${record.timestamp}"
    val existingLines = if (Files.exists(paymentPath)) {
      Files.readAllLines(paymentPath).asScala.toList
    } else {
      List()
    }
    val filteredLines = existingLines.filterNot(_.startsWith(s"${record.orderId}|"))
    val updatedLines = (filteredLines :+ line).asJava
    Files.write(paymentPath, updatedLines, StandardCharsets.UTF_8)
  }

  def loadPayments(): List[(String, String, Double, String, Long)] = {
    if (!Files.exists(paymentPath)) return List()
    Files.readAllLines(paymentPath).asScala.map { line =>
      val parts = line.split("\\|")
      if (parts.length >= 5) {
        (parts(0), parts(1), parts(2).toDouble, parts(3), parts(4).toLong)
      } else {
        ("unknown", "unknown", 0.0, "unknown", System.currentTimeMillis())
      }
    }.filter(_._1 != "unknown").toList
  }

}

