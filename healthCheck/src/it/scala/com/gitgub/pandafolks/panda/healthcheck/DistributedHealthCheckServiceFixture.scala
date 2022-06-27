package com.gitgub.pandafolks.panda.healthcheck

import cats.effect.Resource
import com.github.pandafolks.panda.healthcheck.{DistributedHealthCheckServiceImpl, HealthCheckConfig, UnsuccessfulHealthCheck, UnsuccessfulHealthCheckDaoImpl}
import com.github.pandafolks.panda.nodestracker.{Node, NodeTrackerDao, NodeTrackerDaoImpl, NodeTrackerService, NodeTrackerServiceImpl}
import com.github.pandafolks.panda.participant.{ParticipantsCache, ParticipantsCacheImpl}
import com.github.pandafolks.panda.participant.event.{ParticipantEvent, ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.pandafolks.mattszm.panda.sequence.{Sequence, SequenceDao}
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import monix.execution.Scheduler
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.scalatest.PrivateMethodTester
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait DistributedHealthCheckServiceFixture extends PrivateMethodTester {
  implicit val scheduler: Scheduler = Scheduler.io("distributed-health-check-service-it-test")

  private val dbName = "test"
  protected val mongoContainer: MongoDBContainer = new MongoDBContainer(
    DockerImageName.parse("mongo").withTag("4.0.10")
  )
  mongoContainer.start()

  private val settings: MongoClientSettings =
    MongoClientSettings.builder()
      .applyToClusterSettings(builder => {
        builder.applyConnectionString(new ConnectionString(mongoContainer.getReplicaSetUrl(dbName)))
        ()
      }
      ).build()

  private val sequenceColName: String = randomString(Sequence.SEQUENCE_COLLECTION_NAME)
  protected val participantEventsColName: String = randomString(ParticipantEvent.PARTICIPANT_EVENTS_COLLECTION_NAME)
  private val sequenceCol: CollectionCodecRef[Sequence] = Sequence.getCollection(dbName, sequenceColName)
  private val participantEventsCol: CollectionCodecRef[ParticipantEvent] = ParticipantEvent.getCollection(dbName, participantEventsColName)
  protected val participantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])] =
    MongoConnection.create2(settings, (participantEventsCol, sequenceCol))
  private val sequenceDao: SequenceDao = new SequenceDao()
  private val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(participantEventsAndSequencesConnection)
  protected val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(participantEventsAndSequencesConnection)

  protected val participantsCache: ParticipantsCache = Await.result(ParticipantsCacheImpl(
    participantEventService = participantEventService,
    List.empty,
    -1 // background refresh job disabled
  ).runToFuture, 5.seconds)
  protected val refreshCachePrivateMethod: PrivateMethod[Task[Unit]] = PrivateMethod[Task[Unit]](Symbol("refreshCache"))

  private val nodesColName: String = randomString(Node.NODES_COLLECTION_NAME)
  private val nodesCol: CollectionCodecRef[Node] = Node.getCollection(dbName, nodesColName)
  private val nodesConnection: Resource[Task, CollectionOperator[Node]] = MongoConnection.create1(settings, nodesCol)
  private val nodeTrackerDao: NodeTrackerDao = new NodeTrackerDaoImpl(nodesConnection)
  private val nodeTrackerService: NodeTrackerService = new NodeTrackerServiceImpl(nodeTrackerDao)(1000)

  protected val unsuccessfulHealthCheckColName: String = randomString(UnsuccessfulHealthCheck.UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME)
  private val unsuccessfulHealthCheckCol: CollectionCodecRef[UnsuccessfulHealthCheck] = UnsuccessfulHealthCheck.getCollection(dbName, unsuccessfulHealthCheckColName)
  protected val unsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]] = MongoConnection.create1(settings, unsuccessfulHealthCheckCol)
  private val unsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDaoImpl = new UnsuccessfulHealthCheckDaoImpl(unsuccessfulHealthCheckConnection)

  protected val serviceUnderTest = new DistributedHealthCheckServiceImpl(
    participantEventService,
    participantsCache,
    nodeTrackerService,
    unsuccessfulHealthCheckDao,
    new ClientStub()
  )(HealthCheckConfig(-1, 2)) // scheduler turned off

  protected val backgroundJobPrivateMethod: PrivateMethod[Task[Unit]] = PrivateMethod[Task[Unit]](Symbol("backgroundJob"))

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get
}
