package com.github.pandafolks.panda.nodestracker

import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.utils.PandaStartupException
import monix.eval.Task
import monix.execution.schedulers.CanBlock
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

private[nodestracker] final class NodeTrackerServiceImpl(
                                    private val nodeTrackerDao: NodeTrackerDao,
                                    private val backgroundJobsRegistry: BackgroundJobsRegistry,
                                  )(
                                    private val fullConsistencyMaxDelayInMillis: Int) extends NodeTrackerService {

  import com.github.pandafolks.panda.utils.scheduler.CoreScheduler.scheduler

  private val logger = LoggerFactory.getLogger(getClass.getName)

  // Every node notifies the tracker about its existence 4 times more often than the maximum time in which we obtain the full consistency.
  private val nodeTrackerRegistrationIntervalInMillis = fullConsistencyMaxDelayInMillis / 4
  private val nodeTrackerDeviationForGetWorkingNodesInMillis = (fullConsistencyMaxDelayInMillis / 2).toLong

  private val nodeId: String = nodeTrackerDao.register()
    .runSyncUnsafe(10.seconds)(scheduler, CanBlock.permit)
    .fold(persistenceError => {
      logger.error("The instance cannot join cluster")
      throw new PandaStartupException(persistenceError.getMessage)
    }, id => {
      logger.info(s"The instance joined the cluster with the ID: $id")
      id
    })

  locally {
    backgroundJobsRegistry.addJobAtFixedRate(0.seconds, nodeTrackerRegistrationIntervalInMillis.millisecond)(
      () => nodeTrackerDao.notify(nodeId)
        .map {
          case Right(_) =>
            logger.debug(s"The cluster has been notified the instance with ID $nodeId is healthy")
            ()
          case Left(error) =>
            logger.error(s"Cannot notify cluster about this instance being alive [id: $nodeId]. Reason: ${error.getMessage}"); ()
        },
      "NodeTrackerServiceNotify"
    )
  }

  override def getNodeId: String = nodeId

  override def getWorkingNodes: Task[List[Node]] = nodeTrackerDao.getNodes(nodeTrackerDeviationForGetWorkingNodesInMillis)
}
