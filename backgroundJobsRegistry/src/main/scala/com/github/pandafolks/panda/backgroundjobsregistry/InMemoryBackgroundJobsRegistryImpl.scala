package com.github.pandafolks.panda.backgroundjobsregistry

import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.FiniteDuration

/** This registry is a per-node one. The job is executed on the node without any synchronization with other panda nodes.
  * It should be used with jobs that need to be run on every node - no matter how many there are.
  */
final class InMemoryBackgroundJobsRegistryImpl(private val scheduler: Scheduler) extends BackgroundJobsRegistry {
  import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry.JobEntry

  private val logger = LoggerFactory.getLogger(getClass.getName)

  private val jobs: ConcurrentLinkedQueue[JobEntry] = new ConcurrentLinkedQueue[JobEntry]()

  override def addJobAtFixedRate(
      initialDelay: FiniteDuration,
      period: FiniteDuration
  )(action: () => Task[Unit], name: String): Unit = {
    if (initialDelay.length >= 0 && period.length > 0) {
      val job: Cancelable = scheduler.scheduleAtFixedRate(initialDelay, period) {
        action().runToFuture(scheduler)
        ()
      }
      jobs.add(JobEntry(job, name))
      logger.info(s"Started \'$name\' background job")
    } else {
      logger.warn(
        s"Could not start \'$name\' background job, because " +
          s"${if (initialDelay.length < 0) "initialDelay argument is smaller than 0"
            else "period argument is smaller of equal to 0"}"
      )
    }
    ()
  }
}
