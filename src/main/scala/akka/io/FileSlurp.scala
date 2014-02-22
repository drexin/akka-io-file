package akka.io

import akka.actor.{Actor, ActorRef}
import java.nio.file.{StandardOpenOption, Path}
import akka.io.File._

class FileSlurp(path: Path, receiver: ActorRef, chunkSize: Int) extends Actor {
  import context.system

  def this(path: Path, receiver: ActorRef) = this(path, receiver, 256)

  var size: Long = _

  override def preStart() {
    IO(File) ! Open(path, StandardOpenOption.READ :: Nil)
  }

  override def receive = {
    case Opened(handler) =>
      handler ! GetSize

    case Size(_size) =>
      size = _size
      sender() ! Read(chunkSize, 0)
      context.become(slurping(0), true)
  }

  def slurping(currentPos: Long): Receive = {
    case res @ ReadResult(_, read,`currentPos`) =>
      receiver ! res
      val nextPos = currentPos + read
      if (nextPos >= size) {
        sender() ! Close
        context.become(closing)
      } else {
        sender() ! Read(chunkSize, nextPos)
        context.become(slurping(nextPos), true)
      }
  }

  def closing: Receive = {
    case Closed =>
      receiver ! FileSlurp.Done
      context.stop(self)
  }
}

object FileSlurp {
  case object Done
}
