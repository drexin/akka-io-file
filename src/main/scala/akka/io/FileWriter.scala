package akka.io

import akka.actor.{ActorRef, Stash, Actor}
import java.nio.file.Path
import akka.util.ByteString
import akka.io.File._
import java.nio.file.StandardOpenOption
import akka.io.File.Open
import akka.io.File.Opened

class FileWriter(path: Path, append: Boolean) extends Actor with Stash {
  import context.system
  import FileWriter._

  var currentPos: Long = 0
  var handler: ActorRef = _

  def this(path: Path) = this(path, false)

  override def preStart() {
    import StandardOpenOption._

    IO(File) ! Open(path, WRITE :: CREATE :: Nil)
  }

  def receive = {
    case Opened(_handler) =>
      handler = _handler
      if (append) handler ! GetSize
      else self ! Size(0)

    case Size(size) =>
      currentPos = size
      context.become(writing, true)
      unstashAll()

    case _ => stash()
  }

  def writing: Receive = {
    case Write(bytes) =>
      handler forward File.Write(bytes, currentPos)
      currentPos += bytes.size

    case WriteLine(bytes) =>
      val newBytes = bytes ++ NL
      handler.forward(File.Write(newBytes, currentPos))
      currentPos += newBytes.size

    case Close =>
      handler ! Close
      context.become(closing(sender()))
  }

  def closing(receiver: ActorRef): Receive = {
    case Closed =>
      receiver ! Closed
      context.stop(self)
  }
}

object FileWriter {
  case class Write(bytes: ByteString)
  case class WriteLine(bytes: ByteString = ByteString.empty)

  private val NL = ByteString(System.lineSeparator())
}
