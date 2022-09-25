package com.github.pandafolks.panda

import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.nodestracker.Node.NODES_COLLECTION_NAME
import com.github.pandafolks.panda.utils.PandaStartupException
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler.scheduler
import monix.connect.mongodb.client.{CollectionCodecRef, MongoConnection}
import monix.eval.Task
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoDatabase}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt


package object nodestracker {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private var nodeTrackerService: Option[NodeTrackerService] = Option.empty

  def launch(
              backgroundJobsRegistry: BackgroundJobsRegistry,
              settings: MongoClientSettings,
              dbName: String,
            )(
              fullConsistencyMaxDelayInMillis: Int
            ): Unit = {
    logger.info("Creating \'nodestracker\' module...")
    val nodesCol: CollectionCodecRef[Node] = Node.getCollection(dbName)
    val nodesConnection = MongoConnection.create1(settings, nodesCol)
    val nodeTrackerDao: NodeTrackerDao = new NodeTrackerDaoImpl(nodesConnection)
    nodeTrackerService = Some(new NodeTrackerServiceImpl(nodeTrackerDao, backgroundJobsRegistry)(fullConsistencyMaxDelayInMillis))

    createModuleRelatedDbIndexes(settings, dbName)
    logger.info("\'nodestracker\' module created successfully")
  }

  def getNodeTrackerService: NodeTrackerService =
    try {
      nodeTrackerService.get
    } catch {
      case _: NoSuchElementException =>
        logger.error("NodeTrackerService not initialized - launch the \'nodestracker\' module firstly")
        throw new PandaStartupException("\'nodestracker\' module is not initialized properly")
    }

  private def createModuleRelatedDbIndexes(settings: MongoClientSettings, dbName: String): Unit = {
    val tmpMongoClient: MongoClient = MongoClient(settings)
    val database: MongoDatabase = tmpMongoClient.getDatabase(dbName)

    logger.debug(s"Creating indexes on the \'$NODES_COLLECTION_NAME\' collection...")
    Task.fromReactivePublisher(
      database.getCollection(NODES_COLLECTION_NAME).createIndexes(
        Seq(
          IndexModel(
            Indexes.compoundIndex(
              Indexes.ascending("lastUpdateTimestamp"),
              Indexes.ascending("_id")
            ),
            IndexOptions().background(false).unique(false)
          ),
        )
      )
    ).runSyncUnsafe(1.minutes)

    tmpMongoClient.close()
    logger.debug(s"Indexes on the \'$NODES_COLLECTION_NAME\' collection successfully created")
  }
}
