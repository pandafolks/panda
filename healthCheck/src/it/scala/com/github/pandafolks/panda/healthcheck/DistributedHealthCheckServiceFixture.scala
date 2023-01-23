package com.github.pandafolks.panda.healthcheck

import cats.effect.Resource
import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl
import com.github.pandafolks.panda.nodestracker._
import com.github.pandafolks.panda.participant.event._
import com.github.pandafolks.panda.participant.{ParticipantsCache, ParticipantsCacheImpl}
import com.github.pandafolks.panda.sequence.{Sequence, SequenceDao, SequenceDaoImpl}
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.scalatest.PrivateMethodTester
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait DistributedHealthCheckServiceFixture extends PrivateMethodTester {
  implicit val scheduler: SchedulerService =
    Scheduler.forkJoin(Runtime.getRuntime.availableProcessors() * 2, Runtime.getRuntime.availableProcessors() * 2)

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

  private val sequenceColName: String = randomString(Sequence.SEQUENCE_COLLECTION_NAME)
  protected val participantEventsColName: String = randomString(ParticipantEvent.PARTICIPANT_EVENTS_COLLECTION_NAME)
  private val sequenceCol: CollectionCodecRef[Sequence] = Sequence.getCollection(dbName, sequenceColName)
  private val participantEventsCol: CollectionCodecRef[ParticipantEvent] =
    ParticipantEvent.getCollection(dbName, participantEventsColName)
  protected val participantEventsAndSequencesConnection
      : Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])] =
    MongoConnection.create2(settings, (participantEventsCol, sequenceCol))
  private val sequenceDao: SequenceDao = new SequenceDaoImpl()
  private val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(
    participantEventsAndSequencesConnection
  )
  protected val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(participantEventsAndSequencesConnection)

  protected val participantsCache: ParticipantsCache = Await.result(
    ParticipantsCacheImpl(
      participantEventService = participantEventService,
      new InMemoryBackgroundJobsRegistryImpl(scheduler),
      List.empty,
      -1 // background refresh job disabled
    ).runToFuture,
    5.seconds
  )
  protected val refreshCachePrivateMethod: PrivateMethod[Task[Unit]] = PrivateMethod[Task[Unit]](Symbol("refreshCache"))

  protected val nodesColName: String = randomString(Node.NODES_COLLECTION_NAME)
  private val nodesCol: CollectionCodecRef[Node] = Node.getCollection(dbName, nodesColName)
  protected val nodesConnection: Resource[Task, CollectionOperator[Node]] = MongoConnection.create1(settings, nodesCol)

  protected val jobsColName: String = randomString(Job.JOBS_COLLECTION_NAME)
  protected val jobsCol: CollectionCodecRef[Job] = Job.getCollection(dbName, jobsColName)
  protected val jobsConnection: Resource[Task, CollectionOperator[Job]] = MongoConnection.create1(settings, jobsCol)

  private val nodeTrackerDao: NodeTrackerDao = new NodeTrackerDaoImpl(nodesConnection)
  private val jobDao: JobDao = new JobDaoImpl(jobsConnection)
  protected val nodeTrackerService: NodeTrackerService =
    new NodeTrackerServiceImpl(nodeTrackerDao, jobDao, new InMemoryBackgroundJobsRegistryImpl(scheduler))(1000)(scheduler)

  protected val unsuccessfulHealthCheckColName: String = randomString(
    UnsuccessfulHealthCheck.UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME
  )
  private val unsuccessfulHealthCheckCol: CollectionCodecRef[UnsuccessfulHealthCheck] =
    UnsuccessfulHealthCheck.getCollection(dbName, unsuccessfulHealthCheckColName)
  protected val unsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]] =
    MongoConnection.create1(settings, unsuccessfulHealthCheckCol)
  protected val unsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDaoImpl = new UnsuccessfulHealthCheckDaoImpl(
    unsuccessfulHealthCheckConnection
  )

  protected val serviceUnderTest = new DistributedHealthCheckServiceImpl(
    participantEventService,
    participantsCache,
    nodeTrackerService,
    unsuccessfulHealthCheckDao,
    new ClientStub(),
    new InMemoryBackgroundJobsRegistryImpl(scheduler)
  )(HealthCheckConfig(-1, 2, Some(5), Some(10), Option.empty))(scheduler) // schedulers turned off

  protected val healthCheckBackgroundJobPrivateMethod: PrivateMethod[Task[Unit]] =
    PrivateMethod[Task[Unit]](Symbol("healthCheckBackgroundJob"))

  protected val markAsNotWorkingBackgroundJobPrivateMethod: PrivateMethod[Task[Unit]] =
    PrivateMethod[Task[Unit]](Symbol("markAsNotWorkingBackgroundJob"))

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get
}
