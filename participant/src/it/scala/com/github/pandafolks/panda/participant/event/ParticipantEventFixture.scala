package com.github.pandafolks.panda.participant.event

import cats.effect.Resource
import com.github.pandafolks.panda.sequence.{Sequence, SequenceDao, SequenceDaoImpl}
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import org.mongodb.scala.{ConnectionString, MongoClientSettings}
import org.scalacheck.Gen
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

trait ParticipantEventFixture {
  protected val dbName = randomString("test")
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

  protected val sequenceColName: String = randomString(Sequence.SEQUENCE_COLLECTION_NAME)
  protected val participantEventsColName: String = randomString(ParticipantEvent.PARTICIPANT_EVENTS_COLLECTION_NAME)
  private val sequenceCol: CollectionCodecRef[Sequence] = Sequence.getCollection(dbName, sequenceColName)
  private val participantEventsCol: CollectionCodecRef[ParticipantEvent] =
    ParticipantEvent.getCollection(dbName, participantEventsColName)

  protected val participantEventsAndSequencesConnection
      : Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])] =
    MongoConnection.create2(settings, (participantEventsCol, sequenceCol))

  protected val sequenceDao: SequenceDao = new SequenceDaoImpl()

  protected val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(
    participantEventsAndSequencesConnection
  )
  protected val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(participantEventsAndSequencesConnection)

  def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(15)).sample.get
}
