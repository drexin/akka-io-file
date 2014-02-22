package akka.io

import java.nio.channels.{CompletionHandler, FileLock, AsynchronousFileChannel}
import akka.actor.{NoSerializationVerificationNeeded, ActorRef, Actor}
import java.nio.ByteBuffer
import akka.util.ByteString

class FileHandler(channel: AsynchronousFileChannel) extends Actor {
  import File._
  import FileHandler._

  var lock: Option[FileLock] = None

  override def receive = {
    case cmd @ Write(bytes, position) =>
      channel.write[AnyRef](bytes.asByteBuffer, position, null, new WriteCompletionHandler(sender(), cmd))

    case cmd @ Read(size, position) =>
      val dst = ByteBuffer.allocate(size)
      channel.read[AnyRef](dst, position, null, new ReadCompletionHandler(sender(), dst, cmd))

    case GetSize =>
      try {
        sender() ! Size(channel.size())
      } catch {
        case e: Exception => sender() ! CommandFailed(GetSize, e)
      }

    case cmd @ Force(metaData) =>
      try {
        channel.force(metaData)
        sender() ! Forced
      } catch {
        case e: Exception => sender ! CommandFailed(cmd, e)
      }

    case cmd @ Truncate(size) =>
      try {
        channel.truncate(size)
        sender() ! Truncated
      } catch {
        case e: Exception => sender() ! CommandFailed(cmd, e)
      }

    case Lock => channel.lock[AnyRef](null, new LockCompletionHandler(self, sender()))

    case Unlock =>
      lock.foreach(_.release())
      lock = None
      sender() ! Unlocked

    case FileLockAcquired(_lock) => lock = Some(_lock)

    case Close =>
      context.stop(self)
      sender ! Closed
  }

  override def postStop = channel.close()

  private[this] sealed trait BasicCompletionHandler[A, B] extends CompletionHandler[A, B] {
    def receiver: ActorRef
    def cmd: Command

    override def failed(exc: Throwable, attachment: B): Unit = receiver ! CommandFailed(cmd, exc)
  }

  private[this] class WriteCompletionHandler(val receiver: ActorRef, val cmd: Write) extends BasicCompletionHandler[Integer, AnyRef] {
    override def completed(result: Integer, attachment: AnyRef): Unit = receiver ! Written(result.intValue())
  }

  private[this] class ReadCompletionHandler(val receiver: ActorRef, dst: ByteBuffer, val cmd: Read) extends BasicCompletionHandler[Integer, AnyRef] {
    override def completed(result: Integer, attachment: AnyRef): Unit = {
      val bytes = Array.ofDim[Byte](result.intValue())
      dst.rewind()
      dst.get(bytes)
      receiver ! ReadResult(ByteString(bytes), result.intValue(), cmd.position)
    }
  }

  private[this] class LockCompletionHandler(fileHandler: ActorRef, val receiver: ActorRef) extends BasicCompletionHandler[FileLock, AnyRef] {
    def cmd = Lock
    override def completed(result: FileLock, attachment: AnyRef): Unit = {
      fileHandler ! FileLockAcquired(result)
      receiver ! Locked
    }
  }
}

object FileHandler {
  private[FileHandler] case class FileLockAcquired(lock: FileLock) extends NoSerializationVerificationNeeded
}
