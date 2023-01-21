package com.github.pandafolks.panda.nodestracker

import cats.effect.Resource
import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

trait NodeTrackerFixture {
  implicit val scheduler: SchedulerService = Scheduler.forkJoin(Runtime.getRuntime.availableProcessors() * 2, Runtime.getRuntime.availableProcessors() * 2)

  private val dbName = "test"
  protected val mongoContainer: MongoDBContainer = new MongoDBContainer(
    DockerImageName.parse("mongo").withTag("latest")
  )
  mongoContainer.start()

  private val settings: MongoClientSettings =
    MongoClientSettings
      .builder()
      .applyToClusterSettings(builder => {
        builder.applyConnectionString(new ConnectionString(mongoContainer.getReplicaSetUrl(dbName)))
        ()
      })
      .build()

  protected val nodesColName: String = randomString(Node.NODES_COLLECTION_NAME)
  protected val nodesCol: CollectionCodecRef[Node] = Node.getCollection(dbName, nodesColName)
  protected val nodesConnection: Resource[Task, CollectionOperator[Node]] = MongoConnection.create1(settings, nodesCol)

  protected val jobsColName: String = randomString(Job.JOBS_COLLECTION_NAME)
  protected val jobsCol: CollectionCodecRef[Job] = Job.getCollection(dbName, jobsColName)
  protected val jobsConnection: Resource[Task, CollectionOperator[Job]] = MongoConnection.create1(settings, jobsCol)

  private val nodeTrackerDao: NodeTrackerDao = new NodeTrackerDaoImpl(nodesConnection)
  private val jobDao: JobDao = new JobDaoImpl(jobsConnection)
  protected val nodeTrackerService: NodeTrackerService =
    new NodeTrackerServiceImpl(nodeTrackerDao, jobDao, new InMemoryBackgroundJobsRegistryImpl(scheduler))(2000)

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get
}
