package com.github.pandafolks.panda.nodestracker

import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.utils.PandaStartupException
import monix.eval.Task
import monix.execution.schedulers.CanBlock
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

final class NodeTrackerServiceImpl(
    private val nodeTrackerDao: NodeTrackerDao,
    private val jobDao: JobDao,
    private val backgroundJobsRegistry: BackgroundJobsRegistry
)(private val fullConsistencyMaxDelayInMillis: Int)
    extends NodeTrackerService {

  import com.github.pandafolks.panda.utils.scheduler.CoreScheduler.scheduler

  private val logger = LoggerFactory.getLogger(getClass.getName)

  // Every node notifies the tracker about its existence 8 times more often than the maximum time in which we obtain the full consistency.
  private val nodeTrackerRegistrationIntervalInMillis: Int = fullConsistencyMaxDelayInMillis / 8
  private val nodeTrackerDeviationForGetWorkingNodesInMillis: Long = (fullConsistencyMaxDelayInMillis / 2).toLong

  private val nodeId: String = nodeTrackerDao
    .register()
    .runSyncUnsafe(10.seconds)(scheduler, CanBlock.permit)
    .fold(
      persistenceError => {
        logger.error("The instance cannot join cluster")
        throw new PandaStartupException(persistenceError.getMessage)
      },
      id => {
        logger.info(s"The instance joined the cluster with the ID: $id")
        id
      }
    )

  locally {
    backgroundJobsRegistry.addJobAtFixedRate(0.seconds, nodeTrackerRegistrationIntervalInMillis.millisecond)(
      () =>
        nodeTrackerDao
          .notify(nodeId)
          .map {
            case Right(_) =>
              logger.debug(s"The cluster has been notified the instance with ID $nodeId is healthy")
              ()
            case Left(error) =>
              logger.error(
                s"Cannot notify cluster about this instance being alive [id: $nodeId]. Reason: ${error.getMessage}"
              )
              ()
          },
      "NodeTrackerServiceNotify"
    )
  }

  override def getNodeId: String = nodeId

  override def getWorkingNodes: Task[List[Node]] =
    nodeTrackerDao.getNodes(nodeTrackerDeviationForGetWorkingNodesInMillis)

  override def isNodeWorking(nodeId: ObjectId): Task[Boolean] =
    nodeTrackerDao.isNodeWorking(nodeId, nodeTrackerDeviationForGetWorkingNodesInMillis)

  override def isCurrentNodeResponsibleForJob(jobName: String): Task[Boolean] = {
    val currentNodeId: String = getNodeId

    def assignCurrentNodeToJob: Task[Boolean] =
      jobDao.assignNodeToJob(jobName, new ObjectId(currentNodeId)).map {
        case Right(_) => true // The current node was successfully assigned, so we are returning true.
        case _ =>
          false // There was some error during the DB operation, so we are returning false because the assignment failed.
      }

    jobDao.find(jobName).flatMap {
      case Some(job) => // the job already exists in the database
        isNodeWorking(job.nodeId).flatMap {
          case true if job.nodeId.toString == currentNodeId =>
            Task.now(true) // The node is working and the node is the current one so we are returning true.
          case true =>
            Task.now(
              false
            ) // The node is working, but this is not the current one (some other healthy/working node is responsible for this job) so we are returning false.
          case false =>
            assignCurrentNodeToJob //  The node is NOT working, so we are trying to assign the current node as the one which will be responsible for this job.
        }
      case None =>
        assignCurrentNodeToJob // If there is no job, we are trying to create one and assign the current node as an executor.
    }
  }

}
