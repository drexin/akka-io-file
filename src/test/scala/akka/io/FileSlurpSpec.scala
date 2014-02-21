package akka.io

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import java.nio.file.Paths
import akka.io.File.ReadResult
import akka.io.FileSlurp.Done

class FileSlurpSpec extends TestKit(ActorSystem("system")) with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  override def afterAll() {
    new java.io.File("/tmp/test-file.txt").delete()
    system.shutdown()
  }

  "A FileSlurp" should {
    "read a whole file in correct order" in {
      printToFile(new java.io.File("/tmp/test-file.txt")) { p =>
        for (i <- 0 until 1000000) p.print(util.Random.nextPrintableChar())
      }

      implicit val system = ActorSystem("system")

      val slurp = system.actorOf(Props(classOf[FileSlurp], Paths.get("/tmp", "test-file.txt"), self))

      watch(slurp)

      val chunks = receiveWhile() {
        case ReadResult(bytes, _, _) => bytes.utf8String
      }

      val content = chunks.mkString

      content.size should be(1000000)
      content should be(io.Source.fromFile("/tmp/test-file.txt").mkString)

      expectMsg(Done)

      expectTerminated(slurp)
    }
  }

  private def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }
}
