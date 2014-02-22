package akka.io

import akka.actor._
import java.nio.file.{ OpenOption, Path }
import java.nio.file.attribute.FileAttribute
import java.nio.channels.AsynchronousFileChannel
import akka.util.ByteString

object File extends ExtensionId[FileExt] with ExtensionIdProvider {

  override def lookup() = File

  override def createExtension(system: ExtendedActorSystem): FileExt = new FileExt(system)

  // commands
  trait Command

  case class Open(file: Path, openOptions: Seq[_ <: OpenOption] = Nil, fileAttributes: Seq[FileAttribute[_]] = Nil) extends Command

  case class Slurp(path: Path, receiver: ActorRef, chunkSize: Int = 256) extends Command

  case class Write(bytes: ByteString, position: Long) extends Command

  case class Read(size: Int, position: Long) extends Command

  case object GetSize extends Command

  case class Force(metaData: Boolean) extends Command

  case class Truncate(size: Long) extends Command

  case object Lock extends Command

  case object Unlock extends Command

  case object Close extends Command

  // events
  trait Event

  case class Opened(handler: ActorRef) extends Event

  case class Size(size: Long) extends Event

  case class Written(bytesWritten: Int) extends Event

  case class ReadResult(bytes: ByteString, bytesRead: Int, position: Long) extends Event

  case class CommandFailed(cmd: Command, cause: Throwable) extends Event

  case object Forced extends Event

  case object Truncated extends Event

  case object Locked extends Event

  case object Unlocked extends Event

  case object Closed extends Event
}

class FileExt(system: ExtendedActorSystem) extends IO.Extension {
  val manager: ActorRef = {
    system.asInstanceOf[ActorSystemImpl].systemActorOf(
      props = Props(classOf[FileManager]).withDeploy(Deploy.local),
      name = "IO-FILE")
  }
}

class FileManager extends Actor {
  import File._
  import scala.collection.JavaConverters._

  val poolConfig = context.system.settings.config.getConfig("akka.io.file.executor")
  lazy val pool = new ThreadPoolConfigurator(poolConfig).pool

  override def receive = {
    case cmd @ Open(file, openOptions, fileAttributes) =>
      try {
        val channel = AsynchronousFileChannel.open(file, openOptions.toSet.asJava, pool, fileAttributes: _*)
        val ref = context.actorOf(Props(classOf[FileHandler], channel))
        sender() ! Opened(ref)
      } catch {
        case e: Exception => sender() ! CommandFailed(cmd, e)
      }

    case Slurp(path, receiver, chunkSize) =>
      context.actorOf(Props(classOf[FileSlurp], path, receiver, chunkSize))
  }
}
