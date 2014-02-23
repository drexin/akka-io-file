package akka.io

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import java.nio.file.Paths
import akka.io.FileWriter.Write
import akka.util.ByteString
import akka.io.File.{Closed, Close, Written}

class FileWriterSpec extends TestKit(ActorSystem("system")) with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  override def afterAll() = {
    new java.io.File("/tmp/test-file.txt").delete()
    new java.io.File("/tmp/test-file2.txt").delete()
    system.shutdown()
  }

  "A FileWriter" should {
    "write to a file in correct order" in {
      val writer = system.actorOf(Props(classOf[FileWriter], Paths.get("/tmp", "test-file.txt")))

      watch(writer)

      writer ! Write(ByteString("test"))
      writer ! Write(ByteString("foo"))
      writer ! Write(ByteString("bar"))

      expectMsgAllOf(Written(4), Written(3), Written(3))

      writer ! Close
      expectMsg(Closed)
      expectTerminated(writer)

      io.Source.fromFile("/tmp/test-file.txt").mkString should be("testfoobar")
    }

    "be able append to a file" in {
      printToFile(new java.io.File("/tmp/test-file2.txt")) { p =>
        p.print("foobar")
      }

      val writer = system.actorOf(Props(classOf[FileWriter], Paths.get("/tmp", "test-file2.txt"), true))

      watch(writer)

      writer ! Write(ByteString("baz"))

      expectMsg(Written(3))

      writer ! Close
      expectMsg(Closed)
      expectTerminated(writer)

      io.Source.fromFile("/tmp/test-file2.txt").mkString should be("foobarbaz")
    }
  }
}
