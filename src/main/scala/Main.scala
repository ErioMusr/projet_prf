import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

object Main {
  def main(args: Array[String]): Unit = {
    println("--- Akka 启动中 ---")

    val rootBehavior = Behaviors.receiveMessage[String] { msg =>
      println(s"收到消息: $msg")
      Behaviors.same
    }

    val system: ActorSystem[String] = ActorSystem(rootBehavior, "HelloAkka")
    system ! "Hello Akka! 终于成功了！"

    Thread.sleep(1000) // 保持进程不立即退出
    system.terminate()
  }
}