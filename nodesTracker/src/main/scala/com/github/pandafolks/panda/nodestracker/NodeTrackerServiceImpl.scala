package com.github.pandafolks.panda.nodestracker

import com.github.pandafolks.panda.utils.PandaStartupException
import monix.eval.Task
import monix.execution.schedulers.CanBlock
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

final class NodeTrackerServiceImpl(private val nodeTrackerDao: NodeTrackerDao)(
  private val fullConsistencyMaxDelay: Int) extends NodeTrackerService {

  import monix.execution.Scheduler.{global => scheduler}

  private val logger = LoggerFactory.getLogger(getClass.getName)

  // Every node notifies the tracker about its existence 4 times more often than the maximum time in which we obtain the full consistency.
  private val nodeTrackerRegistrationIntervalInMillis = fullConsistencyMaxDelay * 1000 / 4
  private val nodeTrackerDeviationForGetWorkingNodesInMillis = (fullConsistencyMaxDelay * 1000 / 2).toLong

  private val nodeId: String = nodeTrackerDao.register()
    .runSyncUnsafe(10.seconds)(scheduler, CanBlock.permit)
    .fold(persistenceError => {
      logger.error("Instance cannot join cluster.")
      throw new PandaStartupException(persistenceError.getMessage)
    }, id => id)

  locally {
    scheduler.scheduleAtFixedRate(0.seconds, nodeTrackerRegistrationIntervalInMillis.millisecond) {
      nodeTrackerDao.notify(nodeId)
        .map {
          case Right(_) => ()
          case Left(error) => logger.error(s"Cannot notify cluster about this instance being alive [id: $nodeId]. Reason: ${error.getMessage}"); ()
        }
        .runToFuture(scheduler)
      ()
    }
  }

  override def getNodeId: String = nodeId

  override def getWorkingNodes: Task[List[Node]] = nodeTrackerDao.getNodes(nodeTrackerDeviationForGetWorkingNodesInMillis)
}
