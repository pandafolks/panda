package com.github.pandafolks.panda.backgroundjobsregistry

import monix.eval.Task
import monix.execution.Cancelable

import scala.concurrent.duration.FiniteDuration

trait BackgroundJobsRegistry {

  /** Adds the action to the registry and runs it immediately as a periodic task that becomes enabled first after the given initial delay,
    * and subsequently with the given period. Executions will commence after initialDelay then initialDelay + period, then initialDelay + 2
    * * period and so on.
    *
    * @param initialDelay
    *   Is the time to wait until the first execution happens
    * @param period
    *   Is the time to wait between 2 successive executions of the task
    * @param action
    *   Is the job action wrapped into [[Task]] to be executed
    * @param name
    *   Is the job name
    * @return
    */
  def addJobAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration)(
      action: () => Task[Unit],
      name: String
  ): Unit

  /** Schedules for execution a periodic task that is first executed after the given initial delay and subsequently with the given delay
    * between the termination of one execution and the commencement of the next.
    *
    * @param initialDelay
    *   Is the time to wait until the first execution happens
    * @param period
    *   Is the time to wait between 2 successive executions of the task
    * @param action
    *   Is the job action wrapped into [[Task]] to be executed
    * @param name
    *   Is the job name
    * @return
    */
  def addJobWithFixedDelay(initialDelay: FiniteDuration, period: FiniteDuration)(
      action: () => Task[Unit],
      name: String
  ): Unit
}

object BackgroundJobsRegistry {

  final case class JobEntry(job: Cancelable, name: String)
}
