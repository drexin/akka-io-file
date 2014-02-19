package akka.io

import com.typesafe.config.Config
import java.util.concurrent._
import akka.dispatch.{MonitorableThreadFactory, ThreadPoolConfig}

private [io] class ThreadPoolConfigurator(config: Config) {
  val pool: ExecutorService = config.getString("type") match {
    case "fork-join-executor" => createForkJoinExecutor(config.getConfig("fork-join-executor"))

    case "thread-pool-executor" => createThreadPoolExecutor(config.getConfig("thread-pool-executor"))
  }

  private def createForkJoinExecutor(config: Config) =
    new ForkJoinPool(
      ThreadPoolConfig.scaledPoolSize(
        config.getInt("parallelism-min"),
        config.getDouble("parallelism-factor"),
        config.getInt("parallelism-max")),
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      MonitorableThreadFactory.doNothing, true)

  private def createThreadPoolExecutor(config: Config) = {
    def createQueue(tpe: String, size: Int): BlockingQueue[Runnable] = tpe match {
      case "array"        => new ArrayBlockingQueue[Runnable](size, false)
      case "" | "linked"  => new LinkedBlockingQueue[Runnable](size)
      case x              => throw new IllegalArgumentException("[%s] is not a valid task-queue-type [array|linked]!" format x)
    }

    val corePoolSize = ThreadPoolConfig.scaledPoolSize(config.getInt("core-pool-size-min"), config.getDouble("core-pool-size-factor"), config.getInt("core-pool-size-max"))
    val maxPoolSize = ThreadPoolConfig.scaledPoolSize(config.getInt("max-pool-size-min"), config.getDouble("max-pool-size-factor"), config.getInt("max-pool-size-max"))

    new ThreadPoolExecutor(
      corePoolSize,
      maxPoolSize,
      config.getDuration("keep-alive-time", TimeUnit.MILLISECONDS),
      TimeUnit.MILLISECONDS,
      createQueue(config.getString("task-queue-type"), config.getInt("task-queue-size"))
    )
  }
}
