package com.github.pandafolks.panda.bootstrap.init

import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.bootstrap.configuration.AppConfiguration
import com.github.pandafolks.panda.db.DbAppClient
import com.github.pandafolks.panda.nodestracker
import com.github.pandafolks.panda.nodestracker.NodeTrackerService
import org.mongodb.scala.MongoClientSettings

/**
 * These modules needs caches to be fulfilled and ready to operate.
 *
 * Example: nodeTrackerService registers the Panda Node inside the instances tracker and marks it as ready to handle work.
 * Since this point in time, other Panda nodes would treat this node as fully operational and would
 * split e.g. health check across all available instances. Going further, the health check mechanism reads participants
 * from the cache. If the cache is empty, this node won't do a single health check, even if other Panda nodes assume it will.
 */
final class ModulesInitializedAfterCachesFulfilled(
                                                    private val dbAppClient: DbAppClient,
                                                    private val appConfiguration: AppConfiguration,
                                                    private val backgroundJobsRegistry: BackgroundJobsRegistry,
                                                  ) extends ModulesInitialization {
  locally {

    nodestracker.launch(
      backgroundJobsRegistry = backgroundJobsRegistry,
    )(
      fullConsistencyMaxDelayInMillis = appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis
    )(
      settings = dbAppClient.getSettings.asInstanceOf[MongoClientSettings],
      dbName = dbAppClient.getDbName
    )

  }

  def getNodeTrackerService: NodeTrackerService = nodestracker.getNodeTrackerService

}
