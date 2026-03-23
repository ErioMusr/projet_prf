
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

object FileStore {
  val inventoryPath = Paths.get("inventory.txt")
  val orderPath = Paths.get("order.txt")

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

  def saveOrder(productId:String,quantity:Int,state:String)={
    val line = List(s"$productId:$quantity:$state").asJava
    Files.write(orderPath, line, StandardCharsets.UTF_8)
  }

}

