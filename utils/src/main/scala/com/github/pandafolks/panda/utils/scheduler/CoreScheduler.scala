package com.github.pandafolks.panda.utils.scheduler

import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import org.slf4j.LoggerFactory

object CoreScheduler {
  implicit final val scheduler: Scheduler = global

  locally {
    LoggerFactory.getLogger(getClass.getName).info(s"Running Panda on the ${getClass.getName} scheduler:" +
      "\n[" +
      s"\n\tavailableProcessors: ${Runtime.getRuntime.availableProcessors()}" +
      s"\n\tSystem properties overrides:" +
      s"\n\t[" +
      s"\n\t\tscala.concurrent.context.minThreads: ${System.getProperty("scala.concurrent.context.minThreads")}" +
      s"\n\t\tscala.concurrent.context.maxThreads: ${System.getProperty("scala.concurrent.context.maxThreads")}" +
      s"\n\t\tscala.concurrent.context.numThreads: ${System.getProperty("scala.concurrent.context.numThreads")}" +
      s"\n\t]" +
      "\n]"
    )
  }
}
