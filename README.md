## Usage

```scala
import akka.io.File
import File._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.nio.charset.Charset
import java.nio.file._

implicit val timeout: Timeout = 5.seconds

// open a file
val openRes = IO(File) ? Open(file = Paths.get("/tmp", "test-file.txt"), openOptions = StandardOpenOption.WRITE :: StandardOpenOption.READ :: StandardOpenOption.CREATE :: Nil)

val fileHandler = openRes.mapTo[Opened].map(_.handler)

// Write to the beginning of the file
val writeRes = fileHandler.flatMap(_ ? Write(ByteString("some text"), 0))

// Read 9 bytes from the beginning of the file
fileHandler.flatMap(_ ? Read(9, 0)).onSuccess {
  case ReadResult(bytes, bytesRead) => println(bytes.decodeString(Charset.forName("utf-8")))
}
```
