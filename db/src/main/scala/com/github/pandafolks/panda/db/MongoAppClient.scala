package com.github.pandafolks.panda.db

import cats.effect.Resource
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import com.github.pandafolks.panda.nodestracker.{Job, Node}
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import com.github.pandafolks.panda.sequence.Sequence
import com.github.pandafolks.panda.user.User
import com.github.pandafolks.panda.user.token.Token
import com.github.pandafolks.panda.utils.PandaStartupException
import com.mongodb.connection.ClusterConnectionMode
import com.mongodb.{ConnectionString, ReadConcern, ReadPreference, WriteConcern}
import monix.connect.mongodb.client.{CollectionCodecRef, CollectionOperator, MongoConnection}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.CanBlock
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential, MongoDatabase, ServerAddress}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

final class MongoAppClient(private val config: DbConfig)(private val scheduler: Scheduler) extends DbAppClient {

  private val baseSettings =
    if (config.connectionString.isDefined)
      MongoClientSettings.builder().applyConnectionString(new ConnectionString(config.connectionString.get))
    else
      MongoClientSettings
        .builder()
        .credential(
          MongoCredential.createCredential(config.username.get, config.dbName, config.password.get.toCharArray)
        )
        .applyToClusterSettings(builder => {
          builder
            .hosts(config.contactPoints.get.map(cp => new ServerAddress(cp.host, cp.port)).asJava)
            .mode(config.mode.get.toLowerCase() match {
              case "multiple" => ClusterConnectionMode.MULTIPLE
              case "load_balanced" =>
                throw new PandaStartupException("MongoDB LOAD_BALANCED cluster mode is not supported.")
              case _ => ClusterConnectionMode.SINGLE
            })
          ()
        })

  private val settings: MongoClientSettings = baseSettings
    .readPreference(ReadPreference.nearest())
    .writeConcern(WriteConcern.MAJORITY)
    .readConcern(ReadConcern.MAJORITY)
    .retryReads(true)
    .retryWrites(true)
    .applyToServerSettings(builder => {
      builder
        .heartbeatFrequency(2000, TimeUnit.MILLISECONDS) // 2 seconds
      ()
    })
    .build()

  private val participantEventsCol: CollectionCodecRef[ParticipantEvent] = ParticipantEvent.getCollection(config.dbName)
  private val sequenceCol: CollectionCodecRef[Sequence] = Sequence.getCollection(config.dbName)
  private val usersCol: CollectionCodecRef[User] = User.getCollection(config.dbName)
  private val tokensCol: CollectionCodecRef[Token] = Token.getCollection(config.dbName)
  private val nodesCol: CollectionCodecRef[Node] = Node.getCollection(config.dbName)
  private val jobsCol: CollectionCodecRef[Job] = Job.getCollection(config.dbName)
  private val unsuccessfulHealthCheckCol: CollectionCodecRef[UnsuccessfulHealthCheck] =
    UnsuccessfulHealthCheck.getCollection(config.dbName)
  private val mappersCol: CollectionCodecRef[Mapper] = Mapper.getCollection(config.dbName)
  private val prefixesCol: CollectionCodecRef[Prefix] = Prefix.getCollection(config.dbName)

  private val participantEventsAndSequencesConnection =
    MongoConnection.create2(settings, (participantEventsCol, sequenceCol))
  private val usersWithTokensConnection = MongoConnection.create2(settings, (usersCol, tokensCol))
  private val nodesConnection = MongoConnection.create1(settings, nodesCol)
  private val jobsConnection = MongoConnection.create1(settings, jobsCol)
  private val unsuccessfulHealthCheckConnection = MongoConnection.create1(settings, unsuccessfulHealthCheckCol)
  private val mappersAndPrefixesConnection = MongoConnection.create2(settings, (mappersCol, prefixesCol))

  override def getParticipantEventsAndSequencesConnection
      : Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])] =
    participantEventsAndSequencesConnection

  override def getUsersWithTokensConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])] =
    usersWithTokensConnection

  override def getNodesConnection: Resource[Task, CollectionOperator[Node]] = nodesConnection

  override def getJobsConnection: Resource[Task, CollectionOperator[Job]] = jobsConnection

  override def getUnsuccessfulHealthCheckConnection: Resource[Task, CollectionOperator[UnsuccessfulHealthCheck]] =
    unsuccessfulHealthCheckConnection

  override def getMappersAndPrefixesConnection: Resource[Task, (CollectionOperator[Mapper], CollectionOperator[Prefix])] =
    mappersAndPrefixesConnection

  locally {
    //    creating indexes
    val tmpMongoClient: MongoClient = MongoClient(settings)
    val database: MongoDatabase = tmpMongoClient.getDatabase(config.dbName)

    (
      Task.fromReactivePublisher(
        database
          .getCollection(User.USERS_COLLECTION_NAME)
          .createIndexes(
            Seq(
              IndexModel(
                Indexes.ascending(User.USERNAME_PROPERTY_NAME),
                IndexOptions().background(false).unique(true)
              ),
              IndexModel(
                Indexes.ascending(User.ID_PROPERTY_NAME),
                IndexOptions().background(false).unique(true)
              )
            )
          )
      ) >>
        Task.fromReactivePublisher(
          database
            .getCollection(ParticipantEvent.PARTICIPANT_EVENTS_COLLECTION_NAME)
            .createIndexes(
              Seq(
                IndexModel(
                  Indexes.compoundIndex(
                    Indexes.ascending(ParticipantEvent.PARTICIPANT_IDENTIFIER_PROPERTY_NAME),
                    Indexes.descending(ParticipantEvent.EVENT_ID_PROPERTY_NAME)
                  ),
                  IndexOptions().background(true).unique(true)
                ),
                IndexModel(
                  Indexes.ascending(ParticipantEvent.EVENT_ID_PROPERTY_NAME),
                  IndexOptions().background(false).unique(true)
                )
              )
            )
        ) >>
        Task.fromReactivePublisher(
          database
            .getCollection(Token.TOKENS_COLLECTION_NAME)
            .createIndexes(
              Seq(
                IndexModel(
                  Indexes.hashed(Token.TEMP_ID_COLLECTION_NAME),
                  IndexOptions().background(false).unique(false)
                )
              )
            )
        ) >>
        Task.fromReactivePublisher(
          database
            .getCollection(Node.NODES_COLLECTION_NAME)
            .createIndexes(
              Seq(
                IndexModel(
                  Indexes.compoundIndex(
                    Indexes.ascending(Node.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME),
                    Indexes.ascending(Node.ID_PROPERTY_NAME)
                  ),
                  IndexOptions().background(false).unique(false)
                )
              )
            )
        ) >>
        Task.fromReactivePublisher(
          database
            .getCollection(UnsuccessfulHealthCheck.UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME)
            .createIndexes(
              Seq(
                IndexModel(
                  Indexes.ascending(UnsuccessfulHealthCheck.IDENTIFIER_PROPERTY_NAME),
                  IndexOptions().background(false).unique(true)
                ),
                IndexModel(
                  Indexes.descending(UnsuccessfulHealthCheck.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME),
                  IndexOptions().background(true).unique(false)
                )
              )
            )
        ) >>
        Task.fromReactivePublisher(
          database
            .getCollection(Mapper.MAPPERS_COLLECTION_NAME)
            .createIndexes(
              Seq(
                IndexModel(
                  Indexes.ascending(Mapper.LAST_UPDATE_TIMESTAMP_PROPERTY_NAME),
                  IndexOptions().background(false).unique(false)
                ),
                IndexModel(
                  Indexes.compoundIndex(
                    Indexes.ascending(Mapper.ROUTE_PROPERTY_NAME),
                    Indexes.ascending(Mapper.HTTP_METHOD_PROPERTY_NAME)
                  ),
                  IndexOptions().background(false).unique(true)
                )
              )
            )
        ) >>
        Task.fromReactivePublisher(
          database
            .getCollection(Prefix.PREFIXES_COLLECTION_NAME)
            .createIndexes(
              Seq(
                IndexModel(
                  Indexes.ascending(Prefix.GROUP_NAME_PROPERTY_NAME),
                  IndexOptions().background(false).unique(true)
                )
              )
            )
        ) >>
        Task.fromReactivePublisher(
          database
            .getCollection(Job.JOBS_COLLECTION_NAME)
            .createIndexes(
              Seq(
                IndexModel(
                  Indexes.ascending(Job.NAME_PROPERTY_NAME),
                  IndexOptions().background(false).unique(true)
                )
              )
            )
        )
    ).runSyncUnsafe(1.minutes)(scheduler, CanBlock.permit)

    tmpMongoClient.close()
  }
}
