package com.github.pandafolks.panda.utils.scheduler

import com.github.pandafolks.panda.utils.SystemProperties
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import org.slf4j.LoggerFactory

object CoreScheduler {
  implicit final val scheduler: Scheduler = global

  locally {
    LoggerFactory.getLogger(getClass.getName).info(s"Running Panda on the ${getClass.getName} scheduler:" +
      "\n[" +
      s"\n\tJava Version: ${Runtime.version()}" +
      s"\n\tAvailable Processors: ${Runtime.getRuntime.availableProcessors()}" +
      s"\n\tSystem properties overrides:" +
      s"\n\t[" +
      s"\n\t\tscala.concurrent.context.minThreads: ${SystemProperties.scalaConcurrentContextMinThreads}" +
      s"\n\t\tscala.concurrent.context.maxThreads: ${SystemProperties.scalaConcurrentContextMaxThreads}" +
      s"\n\t\tscala.concurrent.context.numThreads: ${SystemProperties.scalaConcurrentContextNumThreads}" +
      s"\n\t]" +
      "\n]"
    )
  }
}
