package com.github.pandafolks.panda.backgroundjobsregistry
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.FiniteDuration

final class InMemoryBackgroundJobsRegistryImpl(private val scheduler: Scheduler) extends BackgroundJobsRegistry {
  import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl.JobEntry

  private val logger = LoggerFactory.getLogger(getClass.getName)

  private val jobs: ConcurrentLinkedQueue[JobEntry] = new ConcurrentLinkedQueue[JobEntry]()

  override def addJobAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(action: () => Task[Unit], name: String): Unit = {
    val job: Cancelable = scheduler.scheduleAtFixedRate(initialDelay, period){
      action().runToFuture(scheduler)
      ()
    }
    jobs.add(JobEntry(job, name))
    logger.info(s"Started \'$name\' background job")
    ()
  }
}

object InMemoryBackgroundJobsRegistryImpl {

  final case class JobEntry(job: Cancelable, name: String)
}
