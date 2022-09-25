package com.github.pandafolks.panda

import com.github.pandafolks.panda.utils.PandaStartupException
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler
import org.slf4j.LoggerFactory

package object backgroundjobsregistry {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private var backgroundJobsRegistry: Option[BackgroundJobsRegistry] = Option.empty

  def launch(): Unit = {
    logger.info("Creating \'backgroundjobsregistry\' module...")
    backgroundJobsRegistry = Some(new InMemoryBackgroundJobsRegistryImpl(CoreScheduler.scheduler))

    logger.info("\'backgroundjobsregistry\' module created successfully")
  }

  def getBackgroundJobsRegistry: BackgroundJobsRegistry =
    try {
      backgroundJobsRegistry.get
    } catch {
      case _: NoSuchElementException =>
        logger.error("BackgroundJobsRegistry not initialized - launch the \'backgroundjobsregistry\' module firstly")
        throw new PandaStartupException("\'backgroundjobsregistry\' module is not initialized properly")
    }
}
